package com.hbm.tileentity.machine;

import java.util.HashMap;

import com.hbm.blocks.machine.MachineNukeFurnace;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.container.ContainerNukeFurnace;
import com.hbm.inventory.gui.GUINukeFurnace;
import com.hbm.items.ItemCustomLore;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemBreedingRod.BreedingRodType;
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
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

public class TileEntityNukeFurnace extends TileEntityLoadedBase implements ISidedInventory, IGUIProvider {
	private static final int TASK_PROCESS = 0;
	private static final int TASK_FUEL = 1;
	private boolean inventoryFingerprintInitialized;
	private int observedInventoryFingerprint;
	private boolean runtimeInitialized;
	private boolean runtimeEligible;
	private boolean runtimeActive;
	private boolean runtimeSettling;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long runtimeProgressTick;
	private int runtimeProgressBase;
	private long clientProgressTick;
	private int clientProgressBase;
	private boolean clientProgressActive;

	private ItemStack slots[];
	
	public int dualCookTime;
	public int dualPower;
	public static final int maxPower = 1000;
	public static final int processingSpeed = 25;
	
	private static final int[] slots_top = new int[] {1};
	private static final int[] slots_bottom = new int[] {2, 0};
	private static final int[] slots_side = new int[] {0};
	
	private String customName;
	
	public TileEntityNukeFurnace() {
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
		this.settleBeforeInventoryMutation();
		if(slots[i] != null)
		{
			ItemStack itemStack = slots[i];
			slots[i] = null;
			markDirty();
			markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			return itemStack;
		} else {
		return null;
		}
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemStack) {
		this.settleBeforeInventoryMutation();
		slots[i] = itemStack;
		if(itemStack != null && itemStack.stackSize > getInventoryStackLimit())
		{
			itemStack.stackSize = getInventoryStackLimit();
		}
		markDirty();
		markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.nukeFurnace";
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
		return true;
	}
	
	public boolean hasItemPower(ItemStack itemStack) {
		return getItemPower(itemStack) > 0;
	}
	
	private static int getItemPower(ItemStack stack) {
		if(stack == null) {
			return 0;
		} else {

			int power = getFuelValue(stack);
			
			return power;
		}
	}
	
	@Override
	public ItemStack decrStackSize(int i, int j) {
		this.settleBeforeInventoryMutation();
		if(slots[i] != null)
		{
			if(slots[i].stackSize <= j)
			{
			ItemStack itemStack = slots[i];
			slots[i] = null;
			markDirty();
			markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			return itemStack;
			}
			ItemStack itemStack1 = slots[i].splitStack(j);
			if (slots[i].stackSize == 0)
			{
				slots[i] = null;
			}
			markDirty();
			markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			
			return itemStack1;
		} else {
			return null;
		}
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		NBTTagList list = nbt.getTagList("items", 10);
		
		dualPower = nbt.getShort("powerTime");
		dualCookTime = nbt.hasKey("cookTime") ? nbt.getShort("cookTime") : nbt.getShort("CookTime");
		runtimeInitialized = false;
		runtimeEligible = false;
		runtimeActive = false;
		runtimeSettling = false;
		lastAccountingTick = Long.MIN_VALUE;
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
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);
		nbt.setShort("powerTime", (short) dualPower);
		nbt.setShort("cookTime", (short) dualCookTime);
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
		if(i == 0)
		{
			if(itemStack.getItem() instanceof ItemCustomLore)
			{
				return true;
			}
			
			return false;
		}
		
		return true;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		if(i == 0)
		{
			if(itemStack.getItem() == ModItems.rod_empty || itemStack.getItem() == ModItems.rod_dual_empty || itemStack.getItem() == ModItems.rod_quad_empty)
			{
				return true;
			}
			
			return false;
		}
		
		return true;
	}
	
	public int getDiFurnaceProgressScaled(int i) {
		long displayed = dualCookTime;
		if(worldObj != null && worldObj.isRemote && clientProgressActive) {
			long elapsed = ((int) worldObj.getTotalWorldTime() - (int) clientProgressTick) & 0xFFFFL;
			displayed = Math.min(processingSpeed, clientProgressBase + elapsed);
		}
		return (int) (displayed * i / processingSpeed);
	}

	private void settleBeforeInventoryMutation() {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote)
			this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	public int getRuntimeProgressTick() { return (int) (runtimeProgressTick & 0xFFFFL); }
	public int getRuntimeProgressBase() { return runtimeProgressBase; }
	public boolean isRuntimeProgressActive() { return runtimeActive; }
	public void setClientProgressState(int tick, int base, boolean active) {
		clientProgressTick = tick & 0xFFFFL;
		clientProgressBase = base;
		clientProgressActive = active;
	}
	
	public int getPowerRemainingScaled(int i) {
		return (dualPower * i) / maxPower;
	}
	
	public boolean canProcess() {
		if(slots[1] == null)
		{
			return false;
		}
        ItemStack itemStack = FurnaceRecipes.smelting().getSmeltingResult(this.slots[1]);
		if(itemStack == null)
		{
			return false;
		}
		
		if(slots[2] == null)
		{
			return true;
		}
		
		if(!slots[2].isItemEqual(itemStack)) {
			return false;
		}
		
		if(slots[2].stackSize < getInventoryStackLimit() && slots[2].stackSize < slots[2].getMaxStackSize()) {
			return true;
		}else{
			return slots[2].stackSize < itemStack.getMaxStackSize();
		}
	}
	
	private void processItem() {
		if(canProcess()) {
	        ItemStack itemStack = FurnaceRecipes.smelting().getSmeltingResult(this.slots[1]);
			
			if(slots[2] == null)
			{
				slots[2] = itemStack.copy();
			}else if(slots[2].isItemEqual(itemStack)) {
				slots[2].stackSize += itemStack.stackSize;
			}
			
			for(int i = 1; i < 2; i++)
			{
				if(slots[i].stackSize <= 0)
				{
					slots[i] = new ItemStack(slots[i].getItem().setFull3D());
				}else{
					slots[i].stackSize--;
				}
				if(slots[i].stackSize <= 0)
				{
					slots[i] = null;
				}
			}
			
			{
				dualPower--;
			}
		}
	}
	
	public boolean hasPower() {
		return dualPower > 0;
	}
	
	public boolean isProcessing() {
		return this.dualCookTime > 0;
	}
	
	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleProgressThrough(now - 1L);
		boolean wasActive = runtimeActive;
		observeInventoryFingerprint();
		runtimeEligible = this.canProcess();
		runtimeInitialized = true;
		if(wasActive) this.settleProgressThrough(now);
		else lastAccountingTick = now;
		this.evaluateAndSchedule(now);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 5 || worldObj == null || worldObj.isRemote) return;
		if(observeInventoryFingerprint()) markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(dualCookTime > 0 && (!runtimeEligible || dualPower <= 0 && !hasItemPower(slots[0]))) {
			dualCookTime = 0;
			runtimeProgressBase = 0;
			runtimeProgressTick = now;
			this.markDirty();
		}
		if(runtimeEligible && dualPower > 0) {
			this.scheduleMachineTransition(now + Math.max(1L, processingSpeed - dualCookTime), TASK_PROCESS, 0);
		} else this.cancelMachineTransition(TASK_PROCESS, 0);
		if(dualPower <= 0 && hasItemPower(slots[0])) this.scheduleMachineTransition(now + 1L, TASK_FUEL, 0);
		else this.cancelMachineTransition(TASK_FUEL, 0);
		this.updateProcessingState(runtimeEligible && dualPower > 0, now);
	}

	private void updateProcessingState(boolean active, long now) {
		if(active && !runtimeActive) {
			runtimeProgressBase = dualCookTime;
			runtimeProgressTick = now;
		} else if(active && dualCookTime == 0 && runtimeProgressBase != 0) {
			runtimeProgressBase = 0;
			runtimeProgressTick = now;
		}
		runtimeActive = active;
		if(worldObj != null && !worldObj.isRemote) {
			boolean shouldBeOn = active || dualCookTime > 0;
			net.minecraft.block.Block desired = shouldBeOn ? com.hbm.blocks.ModBlocks.machine_nuke_furnace_on : com.hbm.blocks.ModBlocks.machine_nuke_furnace_off;
			if(worldObj.getBlock(xCoord, yCoord, zCoord) != desired)
				MachineNukeFurnace.updateBlockState(shouldBeOn, worldObj, xCoord, yCoord, zCoord);
		}
	}

	private void settleProgressThrough(long targetTick) {
		if(runtimeSettling || lastAccountingTick == Long.MIN_VALUE || targetTick <= lastAccountingTick) return;
		long remaining = targetTick - lastAccountingTick;
		runtimeSettling = true;
		try {
			while(remaining > 0L) {
				if(!runtimeEligible || dualPower <= 0) {
					dualCookTime = 0;
					lastAccountingTick = targetTick;
					break;
				}
				long toCompletion = Math.max(1L, processingSpeed - dualCookTime);
				long steps = Math.min(remaining, toCompletion);
				dualCookTime += (int) steps;
				lastAccountingTick += steps;
				remaining -= steps;
				if(dualCookTime >= processingSpeed) {
					dualCookTime = 0;
					this.processItem();
					runtimeEligible = this.canProcess();
					runtimeProgressBase = 0;
					runtimeProgressTick = lastAccountingTick;
					if(!runtimeEligible || dualPower <= 0) {
						if(remaining > 0L) dualCookTime = 0;
						lastAccountingTick = targetTick;
						break;
					}
				}
			}
		} finally {
			runtimeSettling = false;
		}
		this.markDirty();
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
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized || taskSlot != 0) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_FUEL) {
			this.settleProgressThrough(now - 1L);
			boolean fuelLoaded = false;
			if(dualPower <= 0 && hasItemPower(slots[0])) {
				dualPower += getItemPower(slots[0]);
				if(slots[0] != null) {
					slots[0].stackSize--;
					if(slots[0].stackSize == 0) slots[0] = slots[0].getItem().getContainerItem(slots[0]);
				}
				fuelLoaded = true;
			}
			if(fuelLoaded) this.markDirty();
			runtimeEligible = this.canProcess();
			this.settleProgressThrough(now);
		} else if(taskType == TASK_PROCESS) {
			this.settleProgressThrough(now);
		} else return;
		if(isInvalid()) return;
		this.evaluateAndSchedule(now);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		runtimeActive = false;
		super.onChunkUnload();
	}

	@Override
	public void updateEntity() { }
	
	private static HashMap<ComparableStack, Integer> fuels = new HashMap();
	//for the int array: [0] => level (1-4) [1] => amount of operations
	
	/* 
	 * I really don't want to have to do this, but it's better then making a new class, for one TE, for not even recipes but just *fuels*
	 * 
	 * Who even uses this furnace? Nobody, but it's better then removing it without prior approval
	 */
	public static void registerFuels() {
		setRecipe(BreedingRodType.TRITIUM, 5);
		setRecipe(BreedingRodType.CO60, 10);
		setRecipe(BreedingRodType.THF, 30);
		setRecipe(BreedingRodType.U235, 50);
		setRecipe(BreedingRodType.NP237, 30);
		setRecipe(BreedingRodType.PU238, 20);
		setRecipe(BreedingRodType.PU239, 50);
		setRecipe(BreedingRodType.RGP, 30);
		setRecipe(BreedingRodType.WASTE, 20);
	}
	
	/** Sets power for single, dual, and quad rods **/
	public static void setRecipe(BreedingRodType type, int power) {
		fuels.put(new ComparableStack(new ItemStack(ModItems.rod, 1, type.ordinal())), power);
		fuels.put(new ComparableStack(new ItemStack(ModItems.rod_dual, 1, type.ordinal())), power * 2);
		fuels.put(new ComparableStack(new ItemStack(ModItems.rod_quad, 1, type.ordinal())), power * 4);
	}
	
	/**
	 * Returns an integer array of the fuel value of a certain stack
	 * @param stack
	 * @return an integer array (possibly null) with two fields, the HEAT value and the amount of operations
	 */
	public static int getFuelValue(ItemStack stack) {
		
		if(stack == null)
			return 0;
		
		ComparableStack sta = new ComparableStack(stack).makeSingular();
		if(fuels.get(sta) != null)
			return fuels.get(sta);
		
		return 0;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerNukeFurnace(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUINukeFurnace(player.inventory, this);
	}
}
