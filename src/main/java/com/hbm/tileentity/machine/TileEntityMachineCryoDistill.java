package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.container.ContainerMachineCryoDistill;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineCryoDistill;
import com.hbm.inventory.recipes.CryoRecipes;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.Tuple.Quartet;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineCryoDistill extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT, IGUIProvider {
	
	public long energyQuanta;
	public static final long maxPower = 1_000_000;
	
	public FluidTank[] tanks;
	private static final int TASK_DISTILL = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedInputType;

	public TileEntityMachineCryoDistill() {
		super(11);
		
		this.tanks = new FluidTank[5];
		this.tanks[0] = new FluidTank(Fluids.AIR, 64_000);
		this.tanks[1] = new FluidTank(Fluids.NITROGEN, 24_000);
		this.tanks[2] = new FluidTank(Fluids.OXYGEN, 24_000);
		this.tanks[3] = new FluidTank(Fluids.KRYPTON, 24_000);
		this.tanks[4] = new FluidTank(Fluids.CARBONDIOXIDE, 24_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.cryoDistillator";
	}

	@Override
	public void updateEntity() {
		// Server work is driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != tanks[0].getTankType()) this.updateConnections();
		observedInputType = tanks[0].getTankType();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_DISTILL || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long oldEnergy = energyQuanta;
		int[] oldFills = new int[] {tanks[0].getFill(), tanks[1].getFill(), tanks[2].getFill(), tanks[3].getFill(), tanks[4].getFill()};
		int oldInventoryFingerprint = this.inventoryFingerprint();
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
			Quartet<FluidStack, FluidStack, FluidStack, FluidStack> output = CryoRecipes.getOutput(tanks[0].getTankType());
			if(output != null && this.canDistill(output)) {
				tanks[0].setFill(tanks[0].getFill() - 100);
				tanks[1].setFill(tanks[1].getFill() + output.getW().fill);
				tanks[2].setFill(tanks[2].getFill() + output.getX().fill);
				tanks[3].setFill(tanks[3].getFill() + output.getY().fill);
				tanks[4].setFill(tanks[4].getFill() + output.getZ().fill);
				this.setStoredEnergyQuanta(energyQuanta - 20_000L);
			}
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
		if(oldEnergy != energyQuanta || oldFills[0] != tanks[0].getFill() || oldFills[1] != tanks[1].getFill() || oldFills[2] != tanks[2].getFill() || oldFills[3] != tanks[3].getFill() || oldFills[4] != tanks[4].getFill() || oldInventoryFingerprint != observedInventoryFingerprint) this.markDirty();
		this.sendOutputFluids();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean inventoryChanged = this.observeInventoryFingerprint();
			this.refreshRuntimeState();
			this.sendRuntimeState();
			if(inventoryChanged) {
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID);
				this.evaluateAndSchedule(worldObj.getTotalWorldTime());
			}
		} else if(cadence == 20) {
			this.updateConnections();
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		buf.writeLong(energyQuanta);
		for(int i = 0; i < 5; i++) tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		energyQuanta = buf.readLong();
		for(int i = 0; i < 5; i++) tanks[i].deserialize(buf);
	}
	
	private void refreshRuntimeState() {
		this.beginMachineFluidMutation();
		try {
			tanks[0].setType(7, slots);
			Quartet<FluidStack, FluidStack, FluidStack, FluidStack> output = CryoRecipes.getOutput(tanks[0].getTankType());
			if(output == null) {
				tanks[1].setTankType(Fluids.NONE);
				tanks[2].setTankType(Fluids.NONE);
				tanks[3].setTankType(Fluids.NONE);
				tanks[4].setTankType(Fluids.NONE);
			} else {
				tanks[1].setTankType(output.getW().type);
				tanks[2].setTankType(output.getX().type);
				tanks[3].setTankType(output.getY().type);
				tanks[4].setTankType(output.getZ().type);
			}
			tanks[1].unloadTank(1, 2, slots);
			tanks[2].unloadTank(3, 4, slots);
			tanks[3].unloadTank(5, 6, slots);
			tanks[4].unloadTank(8, 9, slots);
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
	}

	private boolean canDistill(Quartet<FluidStack, FluidStack, FluidStack, FluidStack> output) {
		return energyQuanta >= 20_000L && tanks[0].getFill() >= 100
				&& tanks[1].getFill() + output.getW().fill <= tanks[1].getMaxFill()
				&& tanks[2].getFill() + output.getX().fill <= tanks[2].getMaxFill()
				&& tanks[3].getFill() + output.getY().fill <= tanks[3].getMaxFill()
				&& tanks[4].getFill() + output.getZ().fill <= tanks[4].getMaxFill();
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasFluidOutput() {
		return tanks[1].getFill() > 0 || tanks[2].getFill() > 0 || tanks[3].getFill() > 0 || tanks[4].getFill() > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		Quartet<FluidStack, FluidStack, FluidStack, FluidStack> output = CryoRecipes.getOutput(tanks[0].getTankType());
		if((output != null && this.canDistill(output)) || this.hasBatteryWork() || this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_DISTILL, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_DISTILL, TASK_SLOT_MAIN);
	}

	private void updateConnections() {
		DirPos[] con = getConPos();
		this.trySubscribe(worldObj, con[5].getX(), con[5].getY(), con[5].getZ(), con[5].getDir());
		this.trySubscribe(tanks[0].getTankType(), worldObj, con[0].getX(), con[0].getY(), con[0].getZ(), con[0].getDir());
	}

	private void sendOutputFluids() {
		DirPos[] con = getConPos();
		for(int i = 1; i < 5; i++) {
			if(tanks[i].getFill() <= 0) continue;
			for(int o = 1; o < 5; o++) this.sendFluid(tanks[i], worldObj, con[o].getX(), con[o].getY(), con[o].getZ(), con[o].getDir());
		}
	}

	private void sendRuntimeState() {
		this.networkPackNT(15);
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
	
	public DirPos[] getConPos() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
		
		return new DirPos[] {
			// Input
			new DirPos(xCoord + dir.offsetX * -1 + rot.offsetX * -3, yCoord - 2, zCoord + dir.offsetZ * -1 + rot.offsetZ * -3, rot),

			// Outputs
			new DirPos(xCoord + dir.offsetX * 4 + rot.offsetX * -2, yCoord - 2, zCoord + dir.offsetZ * 4 + rot.offsetZ * -2, dir),
			new DirPos(xCoord + dir.offsetX * 4 + rot.offsetX * -1, yCoord - 2, zCoord + dir.offsetZ * 4 + rot.offsetZ * -1, dir),
			new DirPos(xCoord + dir.offsetX * 4 + rot.offsetX * 1, yCoord - 2, zCoord + dir.offsetZ * 4 + rot.offsetZ * 1, dir),
			new DirPos(xCoord + dir.offsetX * 4 + rot.offsetX * 2, yCoord - 2, zCoord + dir.offsetZ * 4 + rot.offsetZ * 2, dir),

			// Power
			new DirPos(xCoord + dir.offsetX * -2 + rot.offsetX * -3, yCoord - 2, zCoord + dir.offsetZ * -2 + rot.offsetZ * -3, rot),
		};
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "o1");
		tanks[2].readFromNBT(nbt, "o2");
		tanks[3].readFromNBT(nbt, "o3");
		tanks[4].readFromNBT(nbt, "o4");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "o1");
		tanks[2].writeToNBT(nbt, "o2");
		tanks[3].writeToNBT(nbt, "o3");
		tanks[4].writeToNBT(nbt, "o4");
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 4,
				yCoord - 2,
				zCoord - 4,
				xCoord + 6,
				yCoord + 5,
				zCoord + 4
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
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public void writeNBT(NBTTagCompound nbt) {
		if(tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && tanks[2].getFill() == 0 && tanks[3].getFill() == 0 && tanks[4].getFill() == 0) return;
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < 5; i++) this.tanks[i].writeToNBT(data, "" + i);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < 5; i++) this.tanks[i].readFromNBT(data, "" + i);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineCryoDistill(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineCryoDistill(player.inventory, this);
	}
}
