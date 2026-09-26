package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.machine.MachineArcFurnace;
import com.hbm.inventory.container.ContainerMachineArcFurnace;
import com.hbm.inventory.gui.GUIMachineArcFurnace;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxElectricityPacket;
import com.hbm.packet.toclient.AuxGaugePacket;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineArcFurnace extends TileEntityLoadedBase implements ISidedInventory, IEnergyReceiverMK2, IGUIProvider, IInfoProviderEC {

	private ItemStack slots[];
	
	public int dualCookTime;
	public long energyQuanta;
	public static final long maxPower = 50000;
	public static final int processingSpeed = 20;
	
	//0: i
	//1: o
	//2: 1
	//3: 2
	//4: 3
	//5: b
	private static final int[] slots_io = new int[] {0, 1, 2, 3, 4, 5};
	
	private String customName;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private boolean observedCanProcess;
	
	public TileEntityMachineArcFurnace() {
		slots = new ItemStack[6];
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
			this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			return itemStack;
		} else {
		return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemStack) {
		slots[i] = itemStack;
		if(itemStack != null && itemStack.stackSize > getInventoryStackLimit())
		{
			itemStack.stackSize = getInventoryStackLimit();
		}
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.arcFurnace";
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
	public boolean isItemValidForSlot(int i, ItemStack itemStack) {
		
		if(i == 2 || i == 3 || i == 4)
			return itemStack.getItem() == ModItems.arc_electrode;
		
		if(i == 0)
			return FurnaceRecipes.smelting().getSmeltingResult(itemStack) != null;
		
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
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
				return itemStack;
			}
			ItemStack itemStack1 = slots[i].splitStack(j);
			if (slots[i].stackSize == 0)
			{
				slots[i] = null;
			}
			this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			
			return itemStack1;
		} else {
			return null;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "powerTime");
		this.dualCookTime = nbt.getInteger("cookTime");
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
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("cookTime", dualCookTime);
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
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		
		if(i == 1)
			return true;
		
		if(i == 2 || i == 3 || i == 4)
			return itemStack.getItem() == ModItems.arc_electrode_burnt;
		
		return false;
	}
	
	public int getDiFurnaceProgressScaled(int i) {
		return (dualCookTime * i) / processingSpeed;
	}
	
	public long getPowerRemainingScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}
	
	public boolean hasPower() {
		return energyQuanta >= 250;
	}
	
	public boolean isProcessing() {
		return this.dualCookTime > 0;
	}
	
	private boolean hasElectrodes() {
		
		if(slots[2] != null && slots[3] != null && slots[4] != null) {
			if((slots[2].getItem() == ModItems.arc_electrode) &&
					(slots[3].getItem() == ModItems.arc_electrode) &&
					(slots[4].getItem() == ModItems.arc_electrode))
				return true;
		}
		
		return false;
	}
	
	public boolean canProcess() {
		
		if(!hasElectrodes())
			return false;
		
		if(slots[0] == null)
		{
			return false;
		}
        ItemStack itemStack = FurnaceRecipes.smelting().getSmeltingResult(this.slots[0]);
        
		if(itemStack == null)
		{
			return false;
		}
		
		if(slots[1] == null)
		{
			return true;
		}
		
		if(!slots[1].isItemEqual(itemStack)) {
			return false;
		}
		
		if(slots[1].stackSize < getInventoryStackLimit() && slots[1].stackSize < slots[1].getMaxStackSize()) {
			return true;
		}else{
			return slots[1].stackSize < itemStack.getMaxStackSize();
		}
	}
	
	private void processItem() {
		if(canProcess()) {
	        ItemStack itemStack = FurnaceRecipes.smelting().getSmeltingResult(this.slots[0]);
			
			if(slots[1] == null)
			{
				slots[1] = itemStack.copy();
			}else if(slots[1].isItemEqual(itemStack)) {
				slots[1].stackSize += itemStack.stackSize;
			}
			
			for(int i = 0; i < 1; i++)
			{
				if(slots[i].stackSize <= 0)
				{
					slots[i] = new ItemStack(slots[i].getItem());
				}else{
					slots[i].stackSize--;
				}
				if(slots[i].stackSize <= 0)
				{
					slots[i] = null;
				}
			}
		}
	}
	
	@Override
	public void updateEntity() {
		// Authoritative processing is driven by MachineRuntime.
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.reconcileRuntimeState(true);
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		int oldProgress = dualCookTime;
		long oldEnergy = energyQuanta;
		int oldInventoryFingerprint = this.inventoryFingerprint();
		runtimeEnergyMutation = true;
		try {
			if(this.hasPower() && this.canProcess()) {
				dualCookTime++;
				this.setStoredEnergyQuanta(Math.max(0L, energyQuanta - 250L));
				if(dualCookTime >= processingSpeed) {
					dualCookTime = 0;
					this.processItem();
				}
			} else {
				dualCookTime = 0;
			}
			this.reconcileRuntimeState(false);
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 5, energyQuanta, maxPower));
		} finally {
			runtimeEnergyMutation = false;
		}
		this.observeInventoryFingerprint();
		if(oldProgress != dualCookTime || oldEnergy != energyQuanta || oldInventoryFingerprint != observedInventoryFingerprint) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		} else if(cadence == 20) {
			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
				this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			boolean canProcess = this.canProcess();
			if(canProcess != observedCanProcess) this.markMachineDirty(MachineDirtyCause.TOPOLOGY | MachineDirtyCause.RECIPE);
			this.sendRuntimeState();
		}
	}

	private void reconcileRuntimeState(boolean resetIneligibleProgress) {
		boolean canProcess = this.canProcess();
		if(resetIneligibleProgress && (!this.hasPower() || !canProcess)) dualCookTime = 0;
		observedCanProcess = canProcess;
		boolean trigger = !(this.hasPower() && canProcess && dualCookTime == 0);
		if(trigger) {
			boolean processing = dualCookTime > 0;
			boolean isOn = worldObj.getBlock(xCoord, yCoord, zCoord) == ModBlocks.machine_arc_furnace_on;
			if(processing != isOn) MachineArcFurnace.updateBlockState(processing, worldObj, xCoord, yCoord, zCoord);
		}
		if(worldObj.getBlock(xCoord, yCoord, zCoord) == ModBlocks.machine_arc_furnace_off) {
			int meta = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
			if(hasElectrodes() && meta <= 5) worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta + 4, 2);
			if(!hasElectrodes() && meta > 5) worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, meta - 4, 2);
		}
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean processing = this.hasPower() && this.canProcess();
		ItemStack battery = slots[5];
		boolean charging = energyQuanta < maxPower && battery != null && (battery.getItem() == ModItems.battery_creative || battery.getItem() == ModItems.fusion_core_infinite);
		if(!charging && energyQuanta < maxPower && battery != null && battery.getItem() instanceof IBatteryItem) {
			IBatteryItem batteryItem = (IBatteryItem) battery.getItem();
			charging = batteryItem.getMaxOutputQuantaPerTick() > 0 && batteryItem.getStoredEnergyQuanta(battery) > 0;
		}
		if(processing || charging || dualCookTime > 0) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
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

	private void sendRuntimeState() {
		if(worldObj == null || worldObj.isRemote) return;
		TargetPoint point = new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 50);
		PacketDispatcher.wrapper.sendToAllAround(new AuxElectricityPacket(xCoord, yCoord, zCoord, energyQuanta), point);
		PacketDispatcher.wrapper.sendToAllAround(new AuxGaugePacket(xCoord, yCoord, zCoord, dualCookTime, 0), point);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
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
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineArcFurnace(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineArcFurnace(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.hasPower() && this.canProcess());
		data.setInteger(CompatEnergyControl.I_PROGRESS, this.dualCookTime);
	}
}
