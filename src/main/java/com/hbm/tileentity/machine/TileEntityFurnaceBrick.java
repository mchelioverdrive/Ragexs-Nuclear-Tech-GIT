package com.hbm.tileentity.machine;

import java.util.HashMap;

import com.hbm.blocks.machine.MachineBrickFurnace;
import com.hbm.inventory.OreDictManager.DictFrame;
import com.hbm.inventory.container.ContainerFurnaceBrick;
import com.hbm.inventory.gui.GUIFurnaceBrick;
import com.hbm.items.ModItems;
import com.hbm.items.ItemEnums.EnumAshType;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.FurnaceGasEmission;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;

public class TileEntityFurnaceBrick extends TileEntityMachineBase implements IGUIProvider {
	
	private static final int[] slotsTop = new int[] { 0 };
	private static final int[] slotsBottom = new int[] { 2, 1, 3 };
	private static final int[] slotsSides = new int[] {1};
	
	public static HashMap<Item, Integer> burnSpeed = new HashMap();
	
	static {
		burnSpeed.put(Items.clay_ball,								4);
		burnSpeed.put(ModItems.ball_fireclay,						4);
		burnSpeed.put(Item.getItemFromBlock(Blocks.netherrack),		4);
		burnSpeed.put(Item.getItemFromBlock(Blocks.cobblestone),	2);
		burnSpeed.put(Item.getItemFromBlock(Blocks.sand),			2);
		burnSpeed.put(Item.getItemFromBlock(Blocks.log),			2);
		burnSpeed.put(Item.getItemFromBlock(Blocks.log2),			2);
	}
	
	public int burnTime;
	public int maxBurnTime;
	public int progress;

	public int ashLevelWood;
	public int ashLevelCoal;
	public int ashLevelMisc;
	private ItemStack cachedResult;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private int observedRecipeCount;
	private boolean runtimeInitialized;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityFurnaceBrick() {
		super(4);
	}

	@Override
	public String getName() {
		return "container.furnaceBrick";
	}

	public void updateEntity() {
		// Server work is driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(15);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_PROCESS || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		boolean wasBurning = burnTime > 0;
		int oldBurnTime = burnTime;
		int oldProgress = progress;
		int oldInventoryFingerprint = this.inventoryFingerprint();
		boolean canOperate = this.breatheAir(wasBurning && worldObj.getTotalWorldTime() % 5 == 0 ? 1 : 0);
		if(burnTime > 0) {
			FurnaceGasEmission.emitCarbonMonoxide(worldObj, xCoord, yCoord, zCoord, 600);
			burnTime--;
		}
		if(burnTime != 0 || slots[1] != null && slots[0] != null) {
			if(canOperate && burnTime == 0 && this.canSmelt()) this.consumeFuelAndAsh();
			if(canOperate && burnTime > 0 && this.canSmelt()) {
				progress += this.getBurnSpeed();
				if(progress >= 200) {
					progress = 0;
					this.smeltItem();
				}
			} else {
				progress = 0;
			}
		}
		if(wasBurning != (burnTime > 0)) MachineBrickFurnace.updateBlockState(burnTime > 0, worldObj, xCoord, yCoord, zCoord);
		boolean inventoryChanged = this.observeInventoryFingerprint();
		if(inventoryChanged) this.markNetworkDirty();
		if(oldBurnTime != burnTime || oldProgress != progress || inventoryChanged) this.markDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNT(15);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5 && this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		else if(cadence == 100) {
			int count = FurnaceRecipes.smelting().getSmeltingList().size();
			if(count != observedRecipeCount) {
				observedRecipeCount = count;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshRuntimeState() {
		this.refreshCachedResult();
		this.observeInventoryFingerprint();
		this.observedRecipeCount = FurnaceRecipes.smelting().getSmeltingList().size();
		if(!this.hasRecipeAndOutputRoom()) progress = 0;
	}

	private void refreshCachedResult() {
		ItemStack result = slots[0] == null ? null : FurnaceRecipes.smelting().getSmeltingResult(slots[0]);
		cachedResult = result == null ? null : result.copy();
	}

	private void consumeFuelAndAsh() {
		maxBurnTime = burnTime = TileEntityFurnace.getItemBurnTime(slots[1]);
		if(burnTime <= 0 || slots[1] == null) return;
		slots[1].stackSize--;
		EnumAshType type = TileEntityFireboxBase.getAshFromFuel(slots[1]);
		if(type == EnumAshType.WOOD) ashLevelWood += burnTime;
		if(type == EnumAshType.COAL) ashLevelCoal += burnTime;
		if(type == EnumAshType.MISC) ashLevelMisc += burnTime;
		int threshold = 2000;
		if(processAsh(ashLevelWood, EnumAshType.WOOD, threshold)) ashLevelWood -= threshold;
		if(processAsh(ashLevelCoal, EnumAshType.COAL, threshold)) ashLevelCoal -= threshold;
		if(processAsh(ashLevelMisc, EnumAshType.MISC, threshold)) ashLevelMisc -= threshold;
		if(slots[1].stackSize == 0) slots[1] = slots[1].getItem().getContainerItem(slots[1]);
	}

	private boolean hasRecipeAndOutputRoom() {
		if(cachedResult == null) return false;
		if(slots[2] == null) return true;
		if(!slots[2].isItemEqual(cachedResult)) return false;
		int result = slots[2].stackSize + cachedResult.stackSize;
		return result <= getInventoryStackLimit() && result <= slots[2].getMaxStackSize();
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(burnTime > 0 || slots[1] != null && slots[0] != null && this.hasRecipeAndOutputRoom()) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
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
	
	public int getBurnSpeed() {
		Integer speed = burnSpeed.get(slots[0].getItem());
		if(speed != null) return speed;
		return 1;
	}
	
	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		return slot >= 2 ? false : (slot == 1 ? TileEntityFurnace.getItemBurnTime(stack) > 0 : true);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return side == 0 ? slotsBottom : (side == 1 ? slotsTop : slotsSides);
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeInt(burnTime);
		buf.writeInt(maxBurnTime);
		buf.writeInt(progress);
	}
	
	@Override public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.burnTime = buf.readInt();
		this.maxBurnTime = buf.readInt();
		this.progress = buf.readInt();
	}
	
	protected boolean processAsh(int level, EnumAshType type, int threshold) {
		
		if(level >= threshold) {
			if(slots[3] == null) {
				slots[3] = DictFrame.fromOne(ModItems.powder_ash, type);
				return true;
			} else if(slots[3].stackSize < slots[3].getMaxStackSize() && slots[3].getItem() == ModItems.powder_ash && slots[3].getItemDamage() == type.ordinal()) {
				slots[3].stackSize++;
				return true;
			}
		}
		
		return false;
	}

	private boolean canSmelt() {
		return this.hasRecipeAndOutputRoom();
	}
	
	public void smeltItem() {
		if(this.canSmelt()) {
			ItemStack itemstack = cachedResult;

			if(this.slots[2] == null) {
				this.slots[2] = itemstack.copy();
			} else if(this.slots[2].getItem() == itemstack.getItem()) {
				this.slots[2].stackSize += itemstack.stackSize;
			}

			--this.slots[0].stackSize;

			if(this.slots[0].stackSize <= 0) {
				this.slots[0] = null;
			}
			this.refreshCachedResult();
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.burnTime = nbt.getInteger("burnTime");
		this.maxBurnTime = nbt.getInteger("maxBurn");
		this.progress = nbt.getInteger("progress");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("burnTime", this.burnTime);
		nbt.setInteger("maxBurn", this.maxBurnTime);
		nbt.setInteger("progress", this.progress);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerFurnaceBrick(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIFurnaceBrick(player.inventory, this);
	}
}
