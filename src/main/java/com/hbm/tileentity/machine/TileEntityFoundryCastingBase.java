package com.hbm.tileentity.machine;

import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMold;
import com.hbm.items.machine.ItemMold.Mold;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Thank god we have a base class now. Now with documentation and as little redundant crap in the child classes as possible.
 * @author hbm
 *
 */
public abstract class TileEntityFoundryCastingBase extends TileEntityFoundryBase implements ISidedInventory {
	private static final int TASK_CAST = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean inputFingerprintInitialized;
	private boolean materialFingerprintInitialized;
	private int observedInputFingerprint;
	private int observedMaterialFingerprint;

	public ItemStack[] slots;
	public TileEntityFoundryCastingBase(int slotCount) {
		slots = new ItemStack[slotCount];
	}
	public int cooloff = 100;

	@Override
	public void updateEntity() {
		super.updateEntity();
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION)) != 0) this.refreshCastingState();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_CAST || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.normalizeCastingBuffer();
		Mold mold = this.getInstalledMold();
		if(mold != null && this.amount == this.getCapacity() && slots[1] == null) {
			cooloff--;
			if(cooloff <= 0) {
				this.amount = 0;
				ItemStack out = mold.getOutput(type);
				if(out != null) slots[1] = out.copy();
				cooloff = 200;
				this.markDirty();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			}
		} else {
			cooloff = 200;
		}
		this.observeInputFingerprint();
		this.observeMaterialFingerprint();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 5 || worldObj == null || worldObj.isRemote) return;
		boolean inventoryChanged = this.observeInputFingerprint();
		boolean materialChanged = this.observeMaterialFingerprint();
		if(inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		if(materialChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	private void refreshCastingState() {
		this.normalizeCastingBuffer();
		if(!this.canAdvanceCasting()) cooloff = 200;
		this.observeInputFingerprint();
		this.observeMaterialFingerprint();
	}

	private void normalizeCastingBuffer() {
		int capacity = this.getCapacity();
		if(this.amount > capacity) this.amount = capacity;
		if(this.amount == 0) this.type = null;
	}

	private boolean canAdvanceCasting() {
		return this.getInstalledMold() != null && this.amount == this.getCapacity() && slots[1] == null;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canAdvanceCasting()) this.scheduleMachineTransition(now + 1L, TASK_CAST, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_CAST, TASK_SLOT_MAIN);
	}

	private int inputFingerprint() {
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

	private boolean observeInputFingerprint() {
		int current = this.inputFingerprint();
		boolean changed = inputFingerprintInitialized && current != observedInputFingerprint;
		observedInputFingerprint = current;
		inputFingerprintInitialized = true;
		return changed;
	}

	private int materialFingerprint() {
		return 31 * System.identityHashCode(type) + amount;
	}

	private boolean observeMaterialFingerprint() {
		int current = this.materialFingerprint();
		boolean changed = materialFingerprintInitialized && current != observedMaterialFingerprint;
		observedMaterialFingerprint = current;
		materialFingerprintInitialized = true;
		return changed;
	}

	@Override
	protected boolean shouldClientReRender() {
		return false;
	}
	
	/** Checks slot 0 to see what mold type is installed. Returns null if no mold is found or an incorrect size was used. */
	public Mold getInstalledMold() {
		if(slots[0] == null) return null;
		
		if(slots[0].getItem() == ModItems.mold) {
			Mold mold = ((ItemMold) slots[0].getItem()).getMold(slots[0]);
			
			if(mold.size == this.getMoldSize())
				return mold;
		}
		
		return null;
	}

	/** Returns the amount of quanta this casting block can hold, depending on the installed mold or 0 if no mold is found. */
	@Override
	public int getCapacity() {
		Mold mold = this.getInstalledMold();
		return mold == null ? 0 : mold.getCost();
	}
	
	/**
	 * Standard check for testing if this material stack can be added to the casting block. Checks:<br>
	 * - type matching<br>
	 * - amount being at max<br>
	 * - whether a mold is installed<br>
	 * - whether the mold can accept this type
	 */
	public boolean standardCheck(World world, int x, int y, int z, ForgeDirection side, MaterialStack stack) {
		if(!super.standardCheck(world, x, y, z, side, stack)) return false; //reject if base conditions are not met
		if(this.slots[1] != null) return false; //reject if a freshly casted item is still present
		Mold mold = this.getInstalledMold();
		if(mold == null) return false;
		
		return mold.getOutput(stack.material) != null; //no OD match -> no pouring
	}
	
	/** Returns an integer determining the mold size, 0 for small molds and 1 for the basin */
	public abstract int getMoldSize();

	@Override
	public ItemStack getStackInSlotOnClosing(int i) {
		if(slots[i] != null) {
			ItemStack itemStack = slots[i];
			slots[i] = null;
			this.onFoundryInventoryChanged();
			return itemStack;
		} else {
			return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemStack) {
		slots[i] = itemStack;
		if(itemStack != null && itemStack.stackSize > getInventoryStackLimit()) {
			itemStack.stackSize = getInventoryStackLimit();
		}
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		this.onFoundryInventoryChanged();
	}
	
	@Override
	public ItemStack decrStackSize(int slot, int amount) {
		worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		if(slots[slot] != null) {
			if(slots[slot].stackSize <= amount) {
				ItemStack itemStack = slots[slot];
				slots[slot] = null;
				this.onFoundryInventoryChanged();
				return itemStack;
			}
			ItemStack itemStack1 = slots[slot].splitStack(amount);
			if(slots[slot].stackSize == 0) {
				slots[slot] = null;
			}
			this.onFoundryInventoryChanged();
			return itemStack1;
		} else {
			return null;
		}
	}

	private void onFoundryInventoryChanged() {
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
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
	public String getInventoryName() {
		return "ntmFoundry";
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override public boolean hasCustomInventoryName() { return false; }
	@Override public boolean isUseableByPlayer(EntityPlayer player) { return false; }
	@Override public boolean isItemValidForSlot(int i, ItemStack stack) { return false; }

	@Override public void openInventory() { }
	@Override public void closeInventory() { }

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		NBTTagList list = nbt.getTagList("items", 10);
		slots = new ItemStack[getSizeInventory()];

		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if(b0 >= 0 && b0 < slots.length) {
				slots[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		NBTTagList list = new NBTTagList();
		
		for(int i = 0; i < slots.length; i++) {
			if(slots[i] != null) {
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte) i);
				slots[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("items", list);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[] { 1 };
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {
		return false;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {
		return slot == 1;
	}
}
