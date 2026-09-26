package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IBatteryItem;
import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.container.ContainerDriveProcessor;
import com.hbm.inventory.gui.GUIMachineDriveProcessor;
import com.hbm.items.ItemVOTVdrive;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCircuit.EnumCircuitType;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.lib.Library;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BufferUtil;
import com.hbm.util.EnumUtil;

import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineDriveProcessor extends TileEntityMachineBase implements IGUIProvider, IControlReceiver, IEnergyReceiverMK2 {
	private static final int TASK_PROCESSING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private static final long OPERATING_POWER_WATTS = EnergyUnits.quantaPerTickToWatts(200L);
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean inventoryFingerprintInitialized;
	private int observedInventoryFingerprint;

	public long energyQuanta;
	public long maxPower = 2_000;

	public boolean isProcessing;
	public int progress;
	public int maxProgress = 100; // 5 seconds

	public String status = "";
	public boolean hasDrive = false;

	private int lastTier;

	public TileEntityMachineDriveProcessor() {
		super(4);
	}

	@Override
	public void updateEntity() {
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		hasDrive = slots[0] != null && slots[0].getItem() == ModItems.full_drive;
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.markNetworkDirty();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESSING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;

		int oldProgress = progress;
		long oldEnergy = energyQuanta;
		boolean oldProcessing = isProcessing;
		boolean oldHasDrive = hasDrive;
		String oldStatus = status;
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 3, energyQuanta, maxPower));

			if(energyQuanta < maxPower * 0.75) {
				isProcessing = false;
				status = EnumChatFormatting.RED + "No power ";
			} else if(slots[0] == null || slots[0].getItem() != ModItems.full_drive) {
				isProcessing = false;
				status = "";
			} else if(getProcessingTier() < ItemVOTVdrive.getProcessingTier(slots[0])) {
				isProcessing = false;
				status = EnumChatFormatting.RED + "Low tier ";
			}

			if(lastTier != getProcessingTier()) status = "";

			if(isProcessing) {
				this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(OPERATING_POWER_WATTS));
				status = EnumChatFormatting.GREEN + "" + EnumChatFormatting.ITALIC + "Processing  ";
				progress++;

				if(progress >= maxProgress) {
					progress = 0;
					isProcessing = false;
					ItemVOTVdrive.setProcessed(slots[0], true);
					status = EnumChatFormatting.GREEN + "Done! ";
					this.onInventorySlotChanged(0);
				}
			} else {
				progress = 0;
			}

			lastTier = getProcessingTier();
			hasDrive = slots[0] != null && slots[0].getItem() == ModItems.full_drive;
		} finally {
			runtimeEnergyMutation = false;
		}

		if(oldProgress != progress || oldEnergy != energyQuanta || oldProcessing != isProcessing || oldHasDrive != hasDrive || !oldStatus.equals(status)) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.networkPackNTIfDirty(15);
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.ENERGY);
		} else if(cadence == 20) {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			this.networkPackNTIfDirty(15);
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

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[3] == null) return false;
		if(slots[3].getItem() == ModItems.battery_creative || slots[3].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[3].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[3].getItem();
		return battery.getStoredEnergyQuanta(slots[3]) > 0 && battery.getMaxOutputQuantaPerTick() > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(runtimeInitialized && (isProcessing || hasBatteryWork())) this.scheduleMachineTransition(now + 1L, TASK_PROCESSING, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PROCESSING, TASK_SLOT_MAIN);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		buf.writeLong(energyQuanta);
		buf.writeBoolean(isProcessing);
		buf.writeInt(progress);
		buf.writeBoolean(hasDrive);

		BufferUtil.writeString(buf, status);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		energyQuanta = buf.readLong();
		isProcessing = buf.readBoolean();
		progress = buf.readInt();
		hasDrive = buf.readBoolean();

		status = BufferUtil.readString(buf);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setBoolean("isProcessing", isProcessing);
		nbt.setInteger("progress", progress);
		nbt.setString("status", status);
		nbt.setInteger("lastTier", lastTier);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		isProcessing = nbt.getBoolean("isProcessing");
		progress = nbt.getInteger("progress");
		status = nbt.getString("status");
		lastTier = nbt.getInteger("lastTier");
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return isUseableByPlayer(player);
	}

	private int getProcessingTier() {
		if(slots[2] == null || slots[2].getItem() != ModItems.circuit) return 0;
		
		EnumCircuitType num = EnumUtil.grabEnumSafely(EnumCircuitType.class, slots[2].getItemDamage());

		switch(num) {
		case PROCESST1: return 1;
		case PROCESST2: return 2;
		case PROCESST3: return 3;
		default: return 0;
		}
	}

	private void processDrive(boolean process) {
		if(!process) {
			isProcessing = false;
			this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
			return;
		}

		if(energyQuanta < maxPower * 0.75) return;
		if(slots[0] == null || slots[0].getItem() != ModItems.full_drive) return;
		if(ItemVOTVdrive.getProcessed(slots[0])) return;

		// Check that our installed upgrade is a high enough tier
		if(getProcessingTier() >= ItemVOTVdrive.getProcessingTier(slots[0])) {
			isProcessing = true;
			this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
		}
	}

	private void cloneDrive() {
		if(energyQuanta < maxPower * 0.75) return;
		if(slots[0] == null || slots[0].getItem() != ModItems.full_drive) return;
		if(slots[1] == null || slots[1].getItem() != ModItems.hard_drive) {
			status = EnumChatFormatting.RED + "No target ";
			return;
		}

		ItemVOTVdrive.markCopied(slots[0]);
		slots[1] = slots[0].copy();

		status = EnumChatFormatting.GREEN + "Drive cloned ";
		this.onInventorySlotChanged(1);
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("process")) {
			processDrive(data.getBoolean("process"));
		}

		if(data.hasKey("clone")) {
			cloneDrive();
		}
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerDriveProcessor(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineDriveProcessor(player.inventory, this);
	}

	@Override
	public String getName() {
		return "container.machineDriveProcessor";
	}

	@Override
	public int getInventoryStackLimit() {
		return 1;
	}

	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
				xCoord - 1,
				yCoord,
				zCoord - 1,
				xCoord + 2,
				yCoord + 1,
				zCoord + 2
			);
		}
		
		return bb;
	}

	@Override public long getStoredEnergyQuanta() { return energyQuanta; }
	@Override public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}
	@Override public long getEnergyCapacityQuanta() { return maxPower; }
	
}
