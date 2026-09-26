package com.hbm.tileentity.machine;

import java.util.HashMap;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.container.ContainerFunnel;
import com.hbm.inventory.gui.GUIFunnel;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.machine.TileEntityMachineAutocrafter.InventoryCraftingAuto;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class TileEntityMachineFunnel extends TileEntityMachineBase implements IGUIProvider, IControlReceiver {
	private static final int TASK_COMPRESS = 1;
	private static final int TASK_SLOT_SHARED = 0;
	private boolean runtimeInitialized;
	private ItemStack[] cachedInputs = new ItemStack[9];
	private int[] cachedInputMeta = new int[9];
	private int[] cachedInputTag = new int[9];
	private ItemStack[] cachedFourResults = new ItemStack[9];
	private ItemStack[] cachedNineResults = new ItemStack[9];
	private int observedInventoryFingerprint;
	private int observedMode;
	private boolean inventoryFingerprintInitialized;
	
	public int mode = 0;
	public static final int MODE_ALL = 0;
	public static final int MODE_3x3 = 1;
	public static final int MODE_2x2 = 2;

	public TileEntityMachineFunnel() {
		super(18);
	}

	@Override
	public String getName() {
		return "container.machineFunnel";
	}

	@Override
	public void updateEntity() {
		// Compression is event driven through MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshRecipeInputs();
		runtimeInitialized = true;
		this.observeInventoryFingerprint();
		observedMode = mode;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(15);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_COMPRESS || taskSlot != TASK_SLOT_SHARED || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		boolean changed = false;
		for(int i = 0; i < 9; i++) changed |= this.compressLane(i);
		this.observeInventoryFingerprint();
		if(changed) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.networkPackNTIfDirty(15);
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote || cadence != 5) return;
		boolean inventoryChanged = this.observeInventoryFingerprint();
		boolean modeChanged = observedMode != mode;
		if(inventoryChanged || modeChanged) {
			if(inventoryChanged) this.refreshRecipeInputs();
			observedMode = mode;
			this.markMachineDirty((inventoryChanged ? MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE : 0) | (modeChanged ? MachineDirtyCause.CONFIGURATION : 0));
		}
	}

	private boolean compressLane(int i) {
		if(slots[i] == null) return false;
		int stacksize = 9;
		ItemStack compressed = (mode == MODE_2x2 || slots[i].stackSize < 9) ? null : cachedNineResults[i];
		if(compressed == null) {
			compressed = (mode == MODE_3x3 || slots[i].stackSize < 4) ? null : cachedFourResults[i];
			stacksize = 4;
		}
		if(compressed == null || slots[i].stackSize < stacksize) return false;
		if(slots[i + 9] == null) {
			slots[i + 9] = compressed.copy();
			this.decrStackSize(i, stacksize);
			return true;
		}
		ItemStack existing = slots[i + 9];
		if(existing.getItem() == compressed.getItem() && existing.getItemDamage() == compressed.getItemDamage() && existing.stackSize + compressed.stackSize <= compressed.getMaxStackSize()) {
			existing.stackSize += compressed.stackSize;
			this.decrStackSize(i, stacksize);
			return true;
		}
		return false;
	}

	private boolean hasCompressibleInput() {
		for(int i = 0; i < 9; i++) {
			if(slots[i] == null) continue;
			int required = 9;
			ItemStack result = (mode == MODE_2x2 || slots[i].stackSize < 9) ? null : cachedNineResults[i];
			if(result == null) {
				result = (mode == MODE_3x3 || slots[i].stackSize < 4) ? null : cachedFourResults[i];
				required = 4;
			}
			if(result == null || slots[i].stackSize < required) continue;
			ItemStack output = slots[i + 9];
			if(output == null || output.getItem() == result.getItem() && output.getItemDamage() == result.getItemDamage() && output.stackSize + result.stackSize <= result.getMaxStackSize()) return true;
		}
		return false;
	}

	private void evaluateAndSchedule(long now) {
		if(runtimeInitialized && this.hasCompressibleInput()) this.scheduleMachineTransition(now + 1L, TASK_COMPRESS, TASK_SLOT_SHARED);
		else this.cancelMachineTransition(TASK_COMPRESS, TASK_SLOT_SHARED);
	}

	private void refreshRecipeInputs() {
		for(int i = 0; i < 9; i++) {
			ItemStack input = slots[i];
			int meta = input == null ? 0 : input.getItemDamage();
			int tagHash = input == null || input.getTagCompound() == null ? 0 : input.getTagCompound().hashCode();
			if(cachedInputs[i] != input || cachedInputMeta[i] != meta || cachedInputTag[i] != tagHash) {
				cachedInputs[i] = input;
				cachedInputMeta[i] = meta;
				cachedInputTag[i] = tagHash;
				cachedFourResults[i] = input == null ? null : this.getFrom4(input);
				cachedNineResults[i] = input == null ? null : this.getFrom9(input);
			}
		}
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
	
	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeInt(this.mode);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		this.mode = buf.readInt();
	}

	public int[] topAccess = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
	public int[] bottomAccess = new int[] { 9, 10, 11, 12, 13, 14, 15, 16, 17 };
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return side == 0 ? bottomAccess : topAccess;
	}

	@Override
	public boolean canExtractItem(int i, ItemStack stack, int j) {
		if(j == 0) return i > 8;
		return j != 1 && i < 9;
	}

	@Override
	public boolean isItemValidForSlot(int slot, ItemStack stack) {
		if(slot > 8) return false;
		if(slots[slot] != null) return true; //if the slot is already occupied, return true because then the same type merging skips the validity check
		return this.getFrom9(stack) != null || this.getFrom4(stack) != null;
	}
	
	protected InventoryCraftingAuto craftingInventory = new InventoryCraftingAuto(3, 3);

	//hashmap lookups are way faster than iterating over the entire ass crafting list all the fucking time
	public static final HashMap<ComparableStack, ItemStack> from4Cache = new HashMap();
	public static final HashMap<ComparableStack, ItemStack> from9Cache = new HashMap();
	
	public ItemStack getFrom4(ItemStack ingredient) {
		ComparableStack singular = new ComparableStack(ingredient).makeSingular();
		if(from4Cache.containsKey(singular)) return from4Cache.get(singular);
		this.craftingInventory.clear();
		this.craftingInventory.setInventorySlotContents(0, ingredient.copy());
		this.craftingInventory.setInventorySlotContents(1, ingredient.copy());
		this.craftingInventory.setInventorySlotContents(3, ingredient.copy());
		this.craftingInventory.setInventorySlotContents(4, ingredient.copy());
		ItemStack match = getMatch(this.craftingInventory);
		from4Cache.put(singular, match != null ? match.copy() : null);
		return match;
	}
	
	public ItemStack getFrom9(ItemStack ingredient) {
		ComparableStack singular = new ComparableStack(ingredient).makeSingular();
		if(from9Cache.containsKey(singular)) return from9Cache.get(singular);
		this.craftingInventory.clear();
		for(int i = 0; i < 9; i++) this.craftingInventory.setInventorySlotContents(i, ingredient.copy());
		ItemStack match = getMatch(this.craftingInventory);
		from9Cache.put(singular, match != null ? match.copy() : null);
		return match;
	}
	
	public ItemStack getMatch(InventoryCrafting grid) {
		for(Object o : CraftingManager.getInstance().getRecipeList()) {
			IRecipe recipe = (IRecipe) o;
			
			if(recipe.matches(grid, worldObj)) {
				return recipe.getCraftingResult(grid);
			}
		}
		return null;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.mode = nbt.getInteger("mode");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("mode", mode);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerFunnel(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIFunnel(player.inventory, this);
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return this.isUseableByPlayer(player);
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		this.mode++;
		if(mode > 2) mode = 0;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}
}
