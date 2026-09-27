package com.hbm.tileentity.machine;

import com.hbm.inventory.container.ContainerMachineKeyForge;
import com.hbm.inventory.gui.GUIMachineKeyForge;
import com.hbm.items.ItemAmmoEnums.Ammo4Gauge;
import com.hbm.items.ModItems;
import com.hbm.items.tool.ItemKeyPin;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public class TileEntityMachineKeyForge extends TileEntityLoadedBase implements ISidedInventory, IGUIProvider {
	private static final int TASK_KEY = 0;
	private boolean inventoryFingerprintInitialized;
	private int observedInventoryFingerprint;

	private ItemStack slots[];

	private static final int[] slots_top = new int[] {0};
	private static final int[] slots_bottom = new int[] {1};
	private static final int[] slots_side = new int[] {2};
	
	private String customName;
	
	public TileEntityMachineKeyForge() {
		slots = new ItemStack[3];
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
			markDirty();
			markMachineDirty(com.hbm.machine.MachineDirtyCause.INVENTORY);
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
		markDirty();
		markMachineDirty(MachineDirtyCause.INVENTORY);
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.keyForge";
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
	
	@Override
	public void openInventory() {}
	@Override
	public void closeInventory() {}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
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
			markDirty();
			markMachineDirty(MachineDirtyCause.INVENTORY);
			return itemStack;
			}
			ItemStack itemStack1 = slots[i].splitStack(j);
			if (slots[i].stackSize == 0)
			{
				slots[i] = null;
			}
			markDirty();
			markMachineDirty(MachineDirtyCause.INVENTORY);
			
			return itemStack1;
		} else {
			return null;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);
		
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
		return true;
	}
	
	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		observeInventoryFingerprint();
		if(hasPendingWork()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_KEY, 0);
		else cancelMachineTransition(TASK_KEY, 0);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 5 || worldObj == null || worldObj.isRemote) return;
		if(observeInventoryFingerprint()) markMachineDirty(MachineDirtyCause.INVENTORY);
		if(hasContinuousWork()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_KEY, 0);
	}

	private boolean hasTransferPair() {
		return slots[0] != null && slots[1] != null && slots[0].getItem() instanceof ItemKeyPin && slots[1].getItem() instanceof ItemKeyPin
				&& ((ItemKeyPin)slots[0].getItem()).canTransfer() && ((ItemKeyPin)slots[1].getItem()).canTransfer();
	}

	private boolean hasContinuousWork() {
		return slots[2] != null && slots[2].getItem() instanceof ItemKeyPin && ((ItemKeyPin)slots[2].getItem()).canTransfer();
	}

	private boolean hasPendingWork() {
		return hasTransferPair() || hasContinuousWork() || (slots[2] != null && slots[2].getItem() == ModItems.ammo_4gauge);
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
		int current = inventoryFingerprint();
		boolean changed = inventoryFingerprintInitialized && current != observedInventoryFingerprint;
		observedInventoryFingerprint = current;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_KEY || taskSlot != 0 || worldObj == null || worldObj.isRemote) return;
		runKeyForgeStep();
		markDirty();
		observeInventoryFingerprint();
		if(!isInvalid() && hasContinuousWork()) scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_KEY, 0);
	}

	@Override
	public void updateEntity() { }

	private void runKeyForgeStep() {

		if(!worldObj.isRemote)
		{
			if(hasTransferPair()) {
				
				ItemKeyPin.setPins(slots[1], ItemKeyPin.getPins(slots[0]));
			}
			
			if(slots[2] != null && slots[2].getItem() instanceof ItemKeyPin && ((ItemKeyPin)slots[2].getItem()).canTransfer()) {
				ItemKeyPin.setPins(slots[2], worldObj.rand.nextInt(900) + 100);
			}

			//DEBUG, remove later
			if(slots[2] != null && slots[2].getItem() == ModItems.ammo_4gauge) {
				slots[2] = ModItems.ammo_4gauge.stackFromEnum(slots[2].stackSize, Ammo4Gauge.QUACK);
			}
		}
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineKeyForge(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineKeyForge(player.inventory, this);
	}
}
