package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.ModBlocks;
import com.hbm.blocks.generic.BlockBobble;
import com.hbm.blocks.generic.BlockBobble.BobbleType;
import com.hbm.inventory.container.ContainerMachineShredder;
import com.hbm.inventory.gui.GUIMachineShredder;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemBlades;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxElectricityPacket;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IBatteryItem;
import api.hbm.energymk2.IEnergyReceiverMK2;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.block.Block;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineShredder extends TileEntityLoadedBase implements ISidedInventory, IEnergyReceiverMK2, IGUIProvider {

	private ItemStack slots[];

	public long energyQuanta;
	public int progress;
	public int soundCycle = 0;
	public static final long maxPower = 10000;
	public static final int processingSpeed = 60;
	
	private static final int[] slots_io = new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29};
	
	private String customName;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeInventoryMutation;
	private boolean runtimeEligible;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long observedRecipeRevision = -1L;
	private final ItemStack[] cachedResults = new ItemStack[9];
	
	public TileEntityMachineShredder() {
		slots = new ItemStack[30];
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
			this.onInventoryChanged(i);
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
		this.onInventoryChanged(i);
	}

	@Override
	public String getInventoryName() {
		return this.hasCustomInventoryName() ? this.customName : "container.machineShredder";
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
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		if(i < 9) return ShredderRecipes.getShredderResult(stack) != null && !(stack.getItem() instanceof ItemBlades);
		if(i == 29) return stack.getItem() instanceof IBatteryItem;
		if(i == 27 || i == 28) return stack.getItem() instanceof ItemBlades;
		
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
				this.onInventoryChanged(i);
				return itemStack;
			}
			ItemStack itemStack1 = slots[i].splitStack(j);
			if (slots[i].stackSize == 0)
			{
				slots[i] = null;
			}
			this.onInventoryChanged(i);
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
		this.progress = nbt.getInteger("progress");
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
		this.runtimeInitialized = false;
		this.inventoryFingerprintInitialized = false;
		this.observedRecipeRevision = -1L;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
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
		nbt.setInteger("progress", progress);
		nbt.setTag("items", list);
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack itemStack, int side) {
		if((slot >= 9 && slot != 27 && slot != 28) || !this.isItemValidForSlot(slot, itemStack))
			return false;
		
		if(slots[slot] == null)
			return true;
		
		int size = slots[slot].stackSize;
		
		for(int k = 0; k < 9; k++) {
			if(slots[k] == null)
				return false;
			
			if(slots[k].getItem() == itemStack.getItem() && slots[k].getItemDamage() == itemStack.getItemDamage() && slots[k].stackSize < size)
				return false;
		}
		
		return true;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		if(i >= 9 && i <= 26) return true;
		if(i >= 27 && i <= 28) if(itemStack.getItemDamage() == itemStack.getMaxDamage() && itemStack.getMaxDamage() > 0) return true;
		
		return false;
	}
	
	public int getDiFurnaceProgressScaled(int i) {
		return (progress * i) / processingSpeed;
	}
	
	public boolean hasPower() {
		return energyQuanta > 0;
	}
	
	public boolean isProcessing() {
		return this.progress > 0;
	}
	
	@Override
	public void updateEntity() {
		// Shredding, battery accounting, and connection maintenance are runtime driven.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshEligibility();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		long beforePower = energyQuanta;
		int beforeProgress = progress;
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 29, energyQuanta, maxPower)); }
		finally { runtimeEnergyMutation = false; }
		boolean canRun = runtimeEligible && hasPower();
		if(canRun && progress + 1 >= processingSpeed) canRun = this.refreshEligibility() && hasPower();
		if(canRun) {
			progress++;
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(this.energyQuanta - 5L); }
			finally { runtimeEnergyMutation = false; }
			if(progress >= processingSpeed) {
				for(int i = 27; i <= 28; i++) if(slots[i].getMaxDamage() > 0) slots[i].setItemDamage(slots[i].getItemDamage() + 1);
				progress = 0;
				runtimeInventoryMutation = true;
				try { this.processItem(); } finally { runtimeInventoryMutation = false; }
				this.refreshEligibility();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			}
			if(soundCycle == 0) worldObj.playSoundEffect(xCoord, yCoord, zCoord, "minecart.base", getVolume(1.0F), 0.75F);
			soundCycle = (soundCycle + 1) % 50;
		} else progress = 0;
		if(beforePower != energyQuanta || beforeProgress != progress) this.markDirty();
		this.evaluateAndSchedule(now);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean changed = this.observeInventoryFingerprint();
			if(changed) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.UPGRADE);
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = observedRecipeRevision != SerializableRecipe.getRegistryRevision();
		if(recipeChanged) {
			observedRecipeRevision = SerializableRecipe.getRegistryRevision();
			this.markMachineDirty(MachineDirtyCause.RECIPE);
		}
		this.updateConnections();
		PacketDispatcher.wrapper.sendToAllAround(new AuxElectricityPacket(xCoord, yCoord, zCoord, energyQuanta), new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 50));
	}

	private boolean refreshEligibility() {
		java.util.Arrays.fill(cachedResults, null);
		runtimeEligible = false;
		if(slots[27] == null || slots[28] == null || getGearLeft() <= 0 || getGearLeft() >= 3 || getGearRight() <= 0 || getGearRight() >= 3) return false;
		for(int i = 0; i < 9; i++) {
			ItemStack input = slots[i];
			if(input == null || input.stackSize <= 0) continue;
			if(input.getItem() == Item.getItemFromBlock(ModBlocks.bobblehead) && input.getItemDamage() == BobbleType.GWEN.ordinal()) {
				worldObj.func_147480_a(xCoord, yCoord, zCoord, false);
				worldObj.newExplosion(null, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, 5, true, true);
				return false;
			}
			ItemStack result = ShredderRecipes.getShredderResult(input);
			if(result != null && hasSpaceForResult(result)) {
				cachedResults[i] = result;
				runtimeEligible = true;
			}
		}
		return runtimeEligible;
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[29] == null) return false;
		if(slots[29].getItem() == ModItems.battery_creative || slots[29].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[29].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[29].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[29]) > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(runtimeEligible && hasPower() || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int tag = stack == null || stack.getItem() instanceof IBatteryItem || stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode();
			int slot = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + tag;
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private void onInventoryChanged(int slot) {
		this.markDirty();
		if(!runtimeInventoryMutation && worldObj != null && !worldObj.isRemote) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | (slot == 29 ? MachineDirtyCause.ENERGY : 0));
	}
	
	private void updateConnections() {
		
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.trySubscribe(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}
	
	public void processItem() {
		
		for(int inpSlot = 0; inpSlot < 9; inpSlot++)
		{
			if(slots[inpSlot] != null && cachedResults[inpSlot] != null && hasSpaceForResult(cachedResults[inpSlot]))
			{
				ItemStack inp = slots[inpSlot];
				ItemStack outp = cachedResults[inpSlot];
				
				boolean flag = false;
				
				for (int outSlot = 9; outSlot < 27; outSlot++)
				{
					if (slots[outSlot] != null && slots[outSlot].getItem() == outp.getItem() && 
							slots[outSlot].getItemDamage() == outp.getItemDamage() &&
							slots[outSlot].stackSize + outp.stackSize <= outp.getMaxStackSize()) {
						
						slots[outSlot].stackSize += outp.stackSize;
						slots[inpSlot].stackSize -= 1;
						flag = true;
						break;
					}
				}
				
				if(!flag)
					for (int outSlot = 9; outSlot < 27; outSlot++)
					{
						if (slots[outSlot] == null) {
							slots[outSlot] = outp.copy();
							slots[inpSlot].stackSize -= 1;
							break;
						}
					}
				
				if(slots[inpSlot].stackSize <= 0)
					slots[inpSlot] = null;
			}
			
		}
	}
	
		
	public boolean canProcess() {

		if(slots[27] != null && slots[28] != null && 
				this.getGearLeft() > 0 && this.getGearLeft() < 3 && 
				this.getGearRight() > 0 && this.getGearRight() < 3) {
			
			for(int i = 0; i < 9; i++)
			{
				if(slots[i] != null && slots[i].getItem() == Item.getItemFromBlock(ModBlocks.bobblehead)&& slots[i].getItemDamage() == BobbleType.GWEN.ordinal()) {
					worldObj.func_147480_a(xCoord, yCoord, zCoord, false);
					worldObj.newExplosion(null, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5, 5, true, true);
					break;
				}
				else if(slots[i] != null && slots[i].stackSize > 0 && hasSpace(slots[i]))
				{
					return true;
				}
			}
		}
		
		return false;
	}
	
	
	public boolean hasSpace(ItemStack stack) {

		ItemStack result = ShredderRecipes.getShredderResult(stack);
		return this.hasSpaceForResult(result);
	}

	private boolean hasSpaceForResult(ItemStack result) {
		if(result == null) return false;
		for(int i = 9; i < 27; i++) {
			if(slots[i] == null) return true;
			if(slots[i].getItem() == result.getItem() && slots[i].getItemDamage() == result.getItemDamage() && slots[i].stackSize + result.stackSize <= result.getMaxStackSize()) return true;
		}
		return false;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.markMachineEnergyDirty();
	}
	
	public long getPowerScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return TileEntityMachineShredder.maxPower;
	}
	
	public int getGearLeft() {
		
		if(slots[27] != null && slots[27].getItem() instanceof ItemBlades)
		{
			if(slots[27].getMaxDamage() == 0)
				return 1;
			
			if(slots[27].getItemDamage() < slots[27].getItem().getMaxDamage()/2)
			{
				return 1;
			} else if(slots[27].getItemDamage() != slots[27].getItem().getMaxDamage()) {
				return 2;
			} else {
				return 3;
			}
		}
		
		return 0;
	}
	
	public int getGearRight() {
		
		if(slots[28] != null && slots[28].getItem() instanceof ItemBlades)
		{
			if(slots[28].getMaxDamage() == 0)
				return 1;
			
			if(slots[28].getItemDamage() < slots[28].getItem().getMaxDamage()/2)
			{
				return 1;
			} else if(slots[28].getItemDamage() != slots[28].getItem().getMaxDamage()) {
				return 2;
			} else {
				return 3;
			}
		}
		
		return 0;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineShredder(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineShredder(player.inventory, this);
	}
}
