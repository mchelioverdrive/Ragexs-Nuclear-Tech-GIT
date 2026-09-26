package com.hbm.tileentity.machine;

import api.hbm.block.ICrucibleAcceptor;
import api.hbm.fluid.IFluidStandardTransceiver;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.container.ContainerMachineStrandCaster;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineStrandCaster;
import com.hbm.inventory.material.Mats;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMold;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.items.machine.ItemScraps;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.NBTPacket;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.util.fauxpointtwelve.DirPos;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

//god thank you bob for this base class
public class TileEntityMachineStrandCaster extends TileEntityFoundryCastingBase implements IGUIProvider, ICrucibleAcceptor, ISidedInventory, IFluidStandardTransceiver, INBTPacketReceiver, IInventory {

	public FluidTank water;
	public FluidTank steam;
	private long lastCastTick = 0;
	private static final int TASK_CAST = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeFluidMutation;
	private boolean inventoryFingerprintInitialized;
	private boolean materialFingerprintInitialized;
	private int observedInventoryFingerprint;
	private int observedMaterialFingerprint;
	private FluidType observedWaterType;
	private ItemMold.Mold cachedMold;
	private ItemStack cachedOutput;
	private boolean cachedCanProcess;

	public String getName() {
		return "container.machineStrandCaster";
	}

	@Override
	public String getInventoryName() {
		return getName();
	}

	public TileEntityMachineStrandCaster() {
		super(7);
		water = new FluidTank(Fluids.FRESH_WATER, 64_000).migrateFrom(Fluids.WATER);
		steam = new FluidTank(Fluids.SPENTSTEAM, 64_000);
		FluidTank.ChangeListener listener = new FluidTank.ChangeListener() {
			@Override public void onTankChanged(FluidTank tank) {
				if(runtimeFluidMutation || worldObj == null || worldObj.isRemote) return;
				markDirty();
				markMachineDirty(MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
			}
		};
		water.setChangeListener(listener);
		steam.setChangeListener(listener);
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote && (this.lastType != this.type || this.lastAmount != this.amount)) {
			worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
			this.lastType = this.type;
			this.lastAmount = this.amount;
		}

		NBTTagCompound data = new NBTTagCompound();

		water.writeToNBT(data, "w");
		steam.writeToNBT(data, "s");

		this.networkPack(data, 150);

	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public String getMachineRuntimeType() { return "hbm:strand_caster"; }

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION)) != 0) this.refreshRuntimeState();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedWaterType != water.getTankType()) this.updateConnections();
		observedWaterType = water.getTankType();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CAST || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int oldAmount = amount;
		int oldWater = water.getFill();
		int oldSteam = steam.getFill();
		int oldInventory = this.inventoryFingerprint();
		if(inventoryFingerprintInitialized && oldInventory != observedInventoryFingerprint || materialFingerprintInitialized && this.materialFingerprint() != observedMaterialFingerprint) this.refreshRuntimeState();
		this.normalizeMaterialBuffer();
		if(this.cachedCanProcess && this.cachedMold != null && amount >= this.getMinimumCastAmount()) {
			int itemsCasted = amount / cachedMold.getCost();
			runtimeFluidMutation = true;
			try {
				for(int j = 0; j < itemsCasted; j++) {
					this.amount -= cachedMold.getCost();
					for(int i = 1; i < 7; i++) {
						if(slots[i] == null) {
							slots[i] = cachedOutput.copy();
							break;
						}
						if(slots[i].isItemEqual(cachedOutput) && slots[i].stackSize + cachedOutput.stackSize <= cachedOutput.getMaxStackSize()) {
							slots[i].stackSize += cachedOutput.stackSize;
							break;
						}
					}
				}
				int waterUsed = this.getWaterRequired() * itemsCasted;
				water.setFill(water.getFill() - waterUsed);
				steam.setFill(steam.getFill() + waterUsed);
			} finally {
				runtimeFluidMutation = false;
			}
			if(amount == 0) type = null;
			lastCastTick = worldObj.getWorldTime();
			cooloffMarkDirty();
			this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
		}
		this.observeInventoryFingerprint();
		this.observeMaterialFingerprint();
		if(oldAmount != amount || oldWater != water.getFill() || oldSteam != steam.getFill() || oldInventory != this.inventoryFingerprint()) {
			this.markDirty();
			this.markChanged();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean inventoryChanged = this.observeInventoryFingerprint();
			boolean materialChanged = this.observeMaterialFingerprint();
			if(inventoryChanged || materialChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		} else if(cadence == 20) {
			this.updateConnections();
		}
	}

	private void cooloffMarkDirty() {
		this.markDirty();
		this.markChanged();
	}

	private void refreshRuntimeState() {
		this.normalizeMaterialBuffer();
		this.cachedMold = this.getInstalledMold();
		this.cachedOutput = type == null || cachedMold == null ? null : cachedMold.getOutput(type);
		this.cachedCanProcess = this.canProcessCached();
		this.observeInventoryFingerprint();
		this.observeMaterialFingerprint();
	}

	private boolean canProcessCached() {
		if(type == null || cachedMold == null || cachedOutput == null) return false;
		for(int i = 1; i < 7; i++) {
			if(slots[i] == null || slots[i].isItemEqual(cachedOutput) && slots[i].stackSize + cachedOutput.stackSize <= cachedOutput.getMaxStackSize())
				return water.getFill() >= this.getWaterRequired() && steam.getFill() < steam.getMaxFill();
		}
		return false;
	}

	private int getMinimumCastAmount() {
		if(cachedMold == null) return Integer.MAX_VALUE;
		return worldObj.getWorldTime() >= lastCastTick + 200 ? cachedMold.getCost() : cachedMold.getCost() * 9;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized || !cachedCanProcess || cachedMold == null || amount < cachedMold.getCost()) {
			this.cancelMachineTransition(TASK_CAST, TASK_SLOT_MAIN);
			return;
		}
		if(amount >= this.getMinimumCastAmount()) {
			this.scheduleMachineTransition(now + 1L, TASK_CAST, TASK_SLOT_MAIN);
		} else {
			long remaining = lastCastTick + 200L - worldObj.getWorldTime();
			this.scheduleMachineTransition(now + Math.max(1L, remaining), TASK_CAST, TASK_SLOT_MAIN);
		}
	}

	private void normalizeMaterialBuffer() {
		int capacity = this.getCapacity();
		if(amount > capacity) {
			ItemStack scrap = ItemScraps.create(new Mats.MaterialStack(type, Math.max(amount - capacity, 0)));
			EntityItem item = new EntityItem(worldObj, xCoord + 0.5, yCoord + 2, zCoord + 0.5, scrap);
			worldObj.spawnEntityInWorld(item);
			amount = capacity;
		}
		if(amount == 0) type = null;
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

	private int materialFingerprint() { return 31 * System.identityHashCode(type) + amount; }

	private boolean observeMaterialFingerprint() {
		int current = this.materialFingerprint();
		boolean changed = materialFingerprintInitialized && current != observedMaterialFingerprint;
		observedMaterialFingerprint = current;
		materialFingerprintInitialized = true;
		return changed;
	}

	public boolean canProcess() {
		ItemMold.Mold mold = this.getInstalledMold();
		if(type != null && mold != null && mold.getOutput(type) != null) {
			for(int i = 1; i < 7; i++) {
				if(slots[i] == null || slots[i].isItemEqual(mold.getOutput(type)) && slots[i].stackSize + mold.getOutput(type).stackSize <= mold.getOutput(type).getMaxStackSize())
					return water.getFill() >= getWaterRequired() && steam.getFill() < steam.getMaxFill();

			}
		}

		return false;
	}

	public DirPos[] getFluidConPos() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new DirPos[] {
			new DirPos(xCoord + rot.offsetX * 2 - dir.offsetX,     yCoord, zCoord + rot.offsetZ * 2 - dir.offsetZ, rot),
			new DirPos(xCoord - rot.offsetX     - dir.offsetX,     yCoord, zCoord - rot.offsetZ     - dir.offsetZ, rot.getOpposite()),
			new DirPos(xCoord + rot.offsetX * 2 - dir.offsetX * 5, yCoord, zCoord + rot.offsetZ * 2 - dir.offsetZ * 5, rot),
			new DirPos(xCoord - rot.offsetX     - dir.offsetX * 5, yCoord, zCoord - rot.offsetZ     - dir.offsetZ * 5, rot.getOpposite())
		};
	}

	public int[][] getMetalPourPos() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);

		return new int[][] {
			new int[] { xCoord + rot.offsetX - dir.offsetX, yCoord + 2, zCoord + rot.offsetZ - dir.offsetZ },
			new int[] { xCoord - dir.offsetX, yCoord + 2, zCoord - dir.offsetZ },
			new int[] { xCoord + rot.offsetX, yCoord + 2, zCoord + rot.offsetZ },
			new int[] { xCoord, yCoord + 2, zCoord }
		};
	}

	@Override
	public ItemMold.Mold getInstalledMold() {
		if(slots[0] == null)
			return null;

		if(slots[0].getItem() == ModItems.mold) {
			return ((ItemMold) slots[0].getItem()).getMold(slots[0]);
		}

		return null;
	}

	@Override
	public int getMoldSize() {
		return getInstalledMold().size;
	}

	@Override
	public boolean canAcceptPartialPour(World world, int x, int y, int z, double dX, double dY, double dZ, ForgeDirection side, Mats.MaterialStack stack) {

		if(side != ForgeDirection.UP)
			return false;
		for(int[] pos : getMetalPourPos()) {
			if(pos[0] == x && pos[1] == y && pos[2] == z) {
				return this.standardCheck(world, x, y, z, side, stack);
			}
		}
		return false;

	}

	@Override
	public boolean standardCheck(World world, int x, int y, int z, ForgeDirection side, Mats.MaterialStack stack) {
		if(this.type != null && this.type != stack.material) return false;
		int limit = this.getInstalledMold() != null ? this.getInstalledMold().getCost() * 9 : this.getCapacity();
		return !(this.amount >= limit || getInstalledMold() == null);
	}

	@Override
	public int getCapacity() {
		ItemMold.Mold mold = this.getInstalledMold();
		return mold == null ? 50000 : mold.getCost() * 10;
	}

	private int getWaterRequired() {
		return getInstalledMold() != null ? 5 * getInstalledMold().getCost() : 50;
	}

	private void updateConnections() {
		for(DirPos pos : getFluidConPos()) {
			this.trySubscribe(water.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.sendFluid(steam, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}
	@Override
	public Mats.MaterialStack standardAdd(World world, int x, int y, int z, ForgeDirection side, Mats.MaterialStack stack) {
		com.hbm.inventory.material.NTMMaterial oldType = this.type;
		int oldAmount = this.amount;
		this.type = stack.material;
		int limit = this.getInstalledMold() != null ? this.getInstalledMold().getCost() * 9 : this.getCapacity();
		if(stack.amount + this.amount <= limit) {
			this.amount += stack.amount;
			this.markMaterialMutation(oldType, oldAmount);
			return null;
		}

		int required = limit - this.amount;
		this.amount = limit;

		stack.amount -= required;
		this.markMaterialMutation(oldType, oldAmount);

		return stack;
	}
	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { steam };
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { water };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { water, steam };
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineStrandCaster(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineStrandCaster(player.inventory, this);
	}

	public void networkPack(NBTTagCompound nbt, int range) {
		if(!worldObj.isRemote)
			PacketDispatcher.wrapper.sendToAllAround(new NBTPacket(nbt, xCoord, yCoord, zCoord), new NetworkRegistry.TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		water.readFromNBT(nbt, "w");
		steam.readFromNBT(nbt, "s");

	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		water.writeToNBT(nbt, "w");
		steam.writeToNBT(nbt, "s");
		nbt.setLong("t", lastCastTick);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		water.readFromNBT(nbt, "w");
		steam.readFromNBT(nbt, "s");
		lastCastTick = nbt.getLong("t");
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		if(i == 0) return stack.getItem() == ModItems.mold;
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int meta) {
		return new int[] { 1, 2, 3, 4, 5, 6 };
	}

	public void markChanged() {
		this.worldObj.markTileEntityChunkModified(this.xCoord, this.yCoord, this.zCoord, this);
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player) {
		if(worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) {
			return false;
		} else {
			return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= 128;
		}
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack itemStack, int side) {
		return this.isItemValidForSlot(slot, itemStack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack itemStack, int side) {
		return !this.isItemValidForSlot(slot, itemStack);
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 7,
				yCoord,
				zCoord - 7,
				xCoord + 7,
				yCoord + 3,
				zCoord + 7);
		}
		return bb;
	}

	public boolean isLoaded = true;

	@Override
	public boolean isLoaded() {
		return isLoaded;
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		this.isLoaded = false;
	}
}
