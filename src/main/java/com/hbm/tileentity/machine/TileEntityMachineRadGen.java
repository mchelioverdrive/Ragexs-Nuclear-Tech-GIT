package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.HashMap;

import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.container.ContainerMachineRadGen;
import com.hbm.inventory.gui.GUIMachineRadGen;
import com.hbm.items.ModItems;
import com.hbm.items.special.ItemWasteLong;
import com.hbm.items.special.ItemWasteShort;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.Tuple.Triplet;

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineRadGen extends TileEntityMachineBase implements IEnergyProviderMK2, IGUIProvider, IInfoProviderEC {
	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_SHARED = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private ItemStack[] cachedInputs = new ItemStack[12];
	private int[] cachedInputMeta = new int[12];
	private int[] cachedInputTag = new int[12];
	private Triplet<Integer, Integer, ItemStack>[] cachedFuel = new Triplet[12];
	private long[] operatingPowerWatts = new long[12];
	private int observedInventoryFingerprint;
	private int observedFuelCount;
	private boolean inventoryFingerprintInitialized;

	public int[] progress = new int[12];
	public int[] maxProgress = new int[12];
	public int[] production = new int[12];
	public ItemStack[] processing = new ItemStack[12];
	protected int output;
	
	public long energyQuanta;
	public static final long maxPower = 1000000;
	
	public boolean isOn = false;

	public TileEntityMachineRadGen() {
		super(24);
	}

	@Override
	public String getName() {
		return "container.radGen";
	}

	@Override
	public void updateEntity() {
		// Generation and energy export are scheduled by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshRuntimeState();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_SHARED || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		if(this.observeInventoryFingerprint()) this.refreshRuntimeState();
		this.output = 0;
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		this.runtimeEnergyMutation = true;
		try {
			this.tryProvide(worldObj, this.xCoord - dir.offsetX * 4, this.yCoord, this.zCoord - dir.offsetZ * 4, dir.getOpposite());
			for(int i = 0; i < 12; i++) {
				if(this.canLoadFuel(i)) {
					progress[i] = 0;
					maxProgress[i] = cachedFuel[i].getY();
					production[i] = cachedFuel[i].getX();
					operatingPowerWatts[i] = EnergyUnits.quantaPerTickToWatts(production[i]);
					processing[i] = new ItemStack(slots[i].getItem(), 1, slots[i].getItemDamage());
					this.decrStackSize(i, 1);
					this.markDirty();
				}
			}
			this.isOn = false;
			for(int i = 0; i < 12; i++) {
				if(processing[i] == null) continue;
				this.isOn = true;
				this.setStoredEnergyQuanta(energyQuanta + EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts[i]));
				this.output += production[i];
				progress[i]++;
				if(progress[i] >= maxProgress[i]) {
					progress[i] = 0;
					ItemStack out = getOutputFromItem(processing[i]);
					if(out != null) {
						if(slots[i + 12] == null) slots[i + 12] = out;
						else slots[i + 12].stackSize += out.stackSize;
					}
					processing[i] = null;
					this.markDirty();
				}
			}
			if(energyQuanta > maxPower) this.setStoredEnergyQuanta(maxPower);
		} finally { runtimeEnergyMutation = false; }
		this.observeInventoryFingerprint();
		this.markDirty();
		this.markNetworkDirty();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		} else if(cadence == 100) {
			if(observedFuelCount != fuels.size()) {
				for(int i = 0; i < 12; i++) { cachedInputs[i] = null; cachedInputMeta[i] = Integer.MIN_VALUE; cachedInputTag[i] = Integer.MIN_VALUE; }
				this.refreshRuntimeState();
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshRuntimeState() {
		for(int i = 0; i < 12; i++) {
			ItemStack input = slots[i];
			int meta = input == null ? 0 : input.getItemDamage();
			int tag = input == null || input.getTagCompound() == null ? 0 : input.getTagCompound().hashCode();
			if(cachedInputs[i] != input || cachedInputMeta[i] != meta || cachedInputTag[i] != tag) {
				cachedInputs[i] = input;
				cachedInputMeta[i] = meta;
				cachedInputTag[i] = tag;
				cachedFuel[i] = input == null ? null : this.grabResult(input);
			}
		}
		for(int i = 0; i < 12; i++) operatingPowerWatts[i] = EnergyUnits.quantaPerTickToWatts(production[i]);
		observedFuelCount = fuels.size();
		this.observeInventoryFingerprint();
	}

	private boolean canLoadFuel(int lane) {
		if(processing[lane] != null || slots[lane] == null || cachedFuel[lane] == null) return false;
		ItemStack outputItem = cachedFuel[lane].getZ();
		if(outputItem == null || slots[lane + 12] == null) return true;
		ItemStack existing = slots[lane + 12];
		return outputItem.getItem() == existing.getItem() && outputItem.getItemDamage() == existing.getItemDamage() && outputItem.stackSize + existing.stackSize <= existing.getMaxStackSize();
	}

	private boolean hasLoadableFuel() {
		for(int i = 0; i < 12; i++) if(this.canLoadFuel(i)) return true;
		return false;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		boolean processingFuel = false;
		for(int i = 0; i < 12; i++) if(processing[i] != null) { processingFuel = true; break; }
		if(energyQuanta > 0 || processingFuel || this.hasLoadableFuel()) this.scheduleMachineTransition(now + 1L, TASK_GENERATE, TASK_SLOT_SHARED);
		else this.cancelMachineTransition(TASK_GENERATE, TASK_SLOT_SHARED);
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
		NBTTagCompound data = new NBTTagCompound();
		data.setIntArray("progress", progress);
		data.setIntArray("maxProgress", maxProgress);
		data.setIntArray("production", production);
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setBoolean("isOn", isOn);
		this.networkPack(data, 50);
	}
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.progress = nbt.getIntArray("progress");
		this.maxProgress = nbt.getIntArray("maxProgress");
		this.production = nbt.getIntArray("production");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.isOn = nbt.getBoolean("isOn");
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.progress = nbt.getIntArray("progress");
		
		if(progress.length != 12) {
			progress = new int[12];
			return;
		}
		
		this.maxProgress = nbt.getIntArray("maxProgress");
		this.production = nbt.getIntArray("production");
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.isOn = nbt.getBoolean("isOn");

		NBTTagList list = nbt.getTagList("progressing", 10);
		for(int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound nbt1 = list.getCompoundTagAt(i);
			byte b0 = nbt1.getByte("slot");
			if(b0 >= 0 && b0 < processing.length) {
				processing[b0] = ItemStack.loadItemStackFromNBT(nbt1);
			}
		}
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setIntArray("progress", this.progress);
		nbt.setIntArray("maxProgress", this.maxProgress);
		nbt.setIntArray("production", this.production);
		EnergyUnits.writeEnergyQuanta(nbt, this.energyQuanta);
		nbt.setBoolean("isOn", this.isOn);
		
		NBTTagList list = new NBTTagList();
		for(int i = 0; i < processing.length; i++) {
			if(processing[i] != null) {
				NBTTagCompound nbt1 = new NBTTagCompound();
				nbt1.setByte("slot", (byte) i);
				processing[i].writeToNBT(nbt1);
				list.appendTag(nbt1);
			}
		}
		nbt.setTag("progressing", list);
		
		EnergyUnits.writeEnergyQuanta(nbt, this.energyQuanta);
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		
		if(i >= 12 || getDurationFromItem(stack) <= 0)
			return false;
		
		if(slots[i] == null)
			return true;
		
		int size = slots[i].stackSize;
		
		for(int j = 0; j < 12; j++) {
			if(slots[j] == null)
				return false;
			
			if(slots[j].getItem() == stack.getItem() && slots[j].getItemDamage() == stack.getItemDamage() && slots[j].stackSize < size)
				return false;
		}
		
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int i) {
		return new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
				12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23};
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i >= 12;
	}
	
	public static final HashMap<ComparableStack, Triplet<Integer, Integer, ItemStack>> fuels = new HashMap();
	
	static {

		for(int i = 0; i < ItemWasteShort.WasteClass.values().length; i++) {
			fuels.put(	new ComparableStack(ModItems.nuclear_waste_short, 1, i),		new Triplet<Integer, Integer, ItemStack>(1500,		30 * 60 * 20,		new ItemStack(ModItems.nuclear_waste_short_depleted, 1, i)));
			fuels.put(	new ComparableStack(ModItems.nuclear_waste_short_tiny, 1, i),	new Triplet<Integer, Integer, ItemStack>(150,		3 * 60 * 20,		new ItemStack(ModItems.nuclear_waste_short_depleted_tiny, 1, i)));
		}
		for(int i = 0; i < ItemWasteLong.WasteClass.values().length; i++) {
			fuels.put(	new ComparableStack(ModItems.nuclear_waste_long, 1, i),			new Triplet<Integer, Integer, ItemStack>(500,		2 * 60 * 60 * 20,	new ItemStack(ModItems.nuclear_waste_long_depleted, 1, i)));
			fuels.put(	new ComparableStack(ModItems.nuclear_waste_long_tiny, 1, i),	new Triplet<Integer, Integer, ItemStack>(50,		12 * 60 * 20,		new ItemStack(ModItems.nuclear_waste_long_depleted_tiny, 1, i)));
		}

		fuels.put(		new ComparableStack(ModItems.scrap_nuclear),					new Triplet<Integer, Integer, ItemStack>(50,		5 * 60 * 20,		null));
		fuels.put(		new ComparableStack(ModItems.gem_rad),							new Triplet<Integer, Integer, ItemStack>(25_000,	30 * 60 * 20,		new ItemStack(Items.diamond)));
	}
	
	private Triplet<Integer, Integer, ItemStack> grabResult(ItemStack stack) {
		return fuels.get(new ComparableStack(stack).makeSingular());
	}
	
	private int getPowerFromItem(ItemStack stack) {
		Triplet<Integer, Integer, ItemStack> result = grabResult(stack);
		if(result == null)
			return 0;
		return result.getX();
	}
	
	private int getDurationFromItem(ItemStack stack) {
		Triplet<Integer, Integer, ItemStack> result = grabResult(stack);
		if(result == null)
			return 0;
		return result.getY();
	}
	
	private ItemStack getOutputFromItem(ItemStack stack) {
		Triplet<Integer, Integer, ItemStack> result = grabResult(stack);
		if(result == null)
			return null;
		if(result.getZ() == null)
			return null;
		return result.getZ().copy();
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
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
		this.markDirty();
		this.markNetworkDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	public long getOutputPowerWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.output);
	}
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineRadGen(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineRadGen(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(output));
	}
}
