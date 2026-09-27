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
	private static final int TASK_BATTERY = 2;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final long OPERATING_POWER_WATTS = EnergyUnits.quantaPerTickToWatts(200L);
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean inventoryFingerprintInitialized;
	private int observedInventoryFingerprint;
	private long lastProgressTick = Long.MIN_VALUE;
	private long clientProgressTick;

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

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastProgressTick = Long.MIN_VALUE;
		super.onChunkUnload();
	}

	@Override
	public void updateEntity() {
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(lastProgressTick == Long.MIN_VALUE) lastProgressTick = now;
		else settleProgressThrough(this.hasBatteryWork() ? now - 1L : now);
		hasDrive = slots[0] != null && slots[0].getItem() == ModItems.full_drive;
		runtimeInitialized = true;
		this.evaluateAndSchedule(now);
		this.markNetworkDirty();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleProgressThrough(now);
			long before = energyQuanta;
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 3, energyQuanta, maxPower)); }
			finally { runtimeEnergyMutation = false; }
			if(before != energyQuanta) this.markNetworkDirty();
			this.evaluateAndSchedule(now);
			return;
		} else if(taskType == TASK_PROCESSING && taskSlot == TASK_SLOT_MAIN) {
			this.settleProgressThrough(now);
		} else return;
		this.evaluateAndSchedule(now);
		this.networkPackNTIfDirty(15);
	}

	/** Progress is settled only through loaded ticks; external writes settle through the preceding tick. */
	private void settleProgressThrough(long target) {
		if(lastProgressTick == Long.MIN_VALUE || target <= lastProgressTick) return;
		long elapsed = target - lastProgressTick;
		lastProgressTick = target;
		if(!isProcessing) return;
		if(slots[0] == null || slots[0].getItem() != ModItems.full_drive || getProcessingTier() < ItemVOTVdrive.getProcessingTier(slots[0])) {
			isProcessing = false;
			progress = 0;
			status = "";
		} else {
			long cost = EnergyUnits.wattsToQuantaPerTick(OPERATING_POWER_WATTS);
			long threshold = maxPower * 3L / 4L;
			long poweredTicks = energyQuanta < threshold ? 0L : (energyQuanta - threshold) / cost + 1L;
			long steps = Math.min(elapsed, Math.min(poweredTicks, maxProgress - progress));
			if(steps > 0L) {
				runtimeEnergyMutation = true;
				try { this.setStoredEnergyQuanta(energyQuanta - steps * cost); }
				finally { runtimeEnergyMutation = false; }
				progress += (int) steps;
				status = EnumChatFormatting.GREEN + "" + EnumChatFormatting.ITALIC + "Processing  ";
			}
			if(progress >= maxProgress) {
				progress = 0;
				isProcessing = false;
				ItemVOTVdrive.setProcessed(slots[0], true);
				status = EnumChatFormatting.GREEN + "Done! ";
				this.onInventorySlotChanged(0);
			} else if(steps < elapsed) {
				isProcessing = false;
				progress = 0;
				status = EnumChatFormatting.RED + "No power ";
			}
		}
		lastTier = getProcessingTier();
		hasDrive = slots[0] != null && slots[0].getItem() == ModItems.full_drive;
		this.markDirty();
		this.markNetworkDirty();
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
		if(!runtimeInitialized) return;
		this.cancelMachineTransition(TASK_PROCESSING, TASK_SLOT_MAIN);
		if(isProcessing) {
			long cost = EnergyUnits.wattsToQuantaPerTick(OPERATING_POWER_WATTS);
			long threshold = maxPower * 3L / 4L;
			long poweredTicks = energyQuanta < threshold ? 1L : (energyQuanta - threshold) / cost + 1L;
			this.scheduleMachineTransition(now + Math.max(1L, Math.min(maxProgress - progress, poweredTicks)), TASK_PROCESSING, TASK_SLOT_MAIN);
		}
		if(hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);

		buf.writeLong(energyQuanta);
		buf.writeBoolean(isProcessing);
		buf.writeInt(progress);
		buf.writeLong(worldObj == null ? 0L : worldObj.getTotalWorldTime());
		buf.writeBoolean(hasDrive);

		BufferUtil.writeString(buf, status);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);

		energyQuanta = buf.readLong();
		isProcessing = buf.readBoolean();
		progress = buf.readInt();
		clientProgressTick = buf.readLong();
		hasDrive = buf.readBoolean();

		status = BufferUtil.readString(buf);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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
			if(worldObj != null) lastProgressTick = worldObj.getTotalWorldTime();
			this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
		}
	}

	private void cloneDrive() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}
	@Override public long getEnergyCapacityQuanta() { return maxPower; }

	@Override protected void beforeInventorySlotChanged(int slot) {
		if((slot == 0 || slot == 2 || slot == 3) && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	public int getDisplayedProgress() {
		if(worldObj == null || !worldObj.isRemote || !isProcessing) return progress;
		return (int) Math.min(maxProgress, progress + Math.max(0L, worldObj.getTotalWorldTime() - clientProgressTick));
	}
	
}
