package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.recipes.AssemblerRecipes;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemAssemblyTemplate;
import com.hbm.lib.Library;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.tileentity.machine.storage.TileEntityCrateTemplate;
import com.hbm.util.InventoryUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public abstract class TileEntityMachineAssemblerBase extends TileEntityMachineBase implements IEnergyReceiverMK2, IGUIProvider {

	public long energyQuanta;
	public int[] progress;
	public int[] maxProgress;
	public boolean isProgressing;
	public boolean[] needsTemplateSwitch;
	private final ItemStack[] cachedTemplateStacks;
	private final Item[] cachedTemplateItems;
	private final int[] cachedTemplateMeta;
	private final int[] cachedTemplateNbtHash;
	private final long[] cachedRecipeGeneration;
	private final AStack[][] cachedRecipes;
	private final ItemStack[] cachedOutputs;
	private final ItemStack[][] cachedOutputArrays;
	private final int[] cachedProcessTimes;
	private final int[][] cachedSlotIndices;
	private final boolean[] runtimeMaterialEligible;
	private final boolean[] runtimeHasRequiredItems;
	private final int[] runtimeItemFingerprints;
	private final long[] runtimeLaneTick;
	private long runtimeRecipeGeneration = -1L;
	private long runtimePowerFingerprint;
	private long runtimeLastAccountingTick = Long.MIN_VALUE;
	private long runtimeLastChargeTick = Long.MIN_VALUE;
	private boolean runtimeFingerprintsInitialized;
	private boolean runtimeEnergyMutation;

	private static final int TASK_ASSEMBLER_LANE = 1;
	private static final int TASK_ASSEMBLER_BATTERY = 2;
	private static final int TASK_SLOT_BATTERY = -1;

	long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(100L);
	int speed = 100;

	public TileEntityMachineAssemblerBase(int scount) {
		super(scount);

		int count = this.getRecipeCount();

		progress = new int[count];
		maxProgress = new int[count];
		needsTemplateSwitch = new boolean[count];
		cachedTemplateStacks = new ItemStack[count];
		cachedTemplateItems = new Item[count];
		cachedTemplateMeta = new int[count];
		cachedTemplateNbtHash = new int[count];
		cachedRecipeGeneration = new long[count];
		cachedRecipes = new AStack[count][];
		cachedOutputs = new ItemStack[count];
		cachedOutputArrays = new ItemStack[count][];
		cachedProcessTimes = new int[count];
		cachedSlotIndices = new int[count][];
		runtimeMaterialEligible = new boolean[count];
		runtimeHasRequiredItems = new boolean[count];
		runtimeLaneTick = new long[count];
		java.util.Arrays.fill(runtimeLaneTick, Long.MIN_VALUE);
		runtimeItemFingerprints = new int[scount];
	}

	@Override
	public void updateEntity() {
		// Production, battery charging, and logistics are owned by MachineRuntime.
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		this.refreshRuntimeSettings(false);
		long recipeGeneration = AssemblerRecipes.recipeGeneration;
		if(recipeGeneration != this.runtimeRecipeGeneration) this.runtimeRecipeGeneration = recipeGeneration;
		long now = worldObj.getTotalWorldTime();
		this.isProgressing = false;
		if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ASSEMBLER_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_ASSEMBLER_BATTERY, TASK_SLOT_BATTERY);
		boolean anyEligible = false;
		for(int i = 0; i < getRecipeCount(); i++) {
			boolean eligible = this.canProcess(i);
			if(eligible && progress[i] > 0) this.isProgressing = true;
			if(eligible) {
				anyEligible = true;
				this.scheduleMachineTransition(now + 1L, TASK_ASSEMBLER_LANE, i);
			} else {
				this.cancelMachineTransition(TASK_ASSEMBLER_LANE, i);
				this.progress[i] = 0;
			}
		}
		if(!anyEligible && !this.hasRuntimeBatteryWork()) this.isProgressing = false;
		this.runtimePowerFingerprint = this.energyQuanta;
		this.runtimeRecipeGeneration = recipeGeneration;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_ASSEMBLER_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			boolean charged = this.beginRuntimeAccounting(now);
			if(charged) {
				this.markDirty();
				this.markNetworkDirty();
				this.runtimePowerFingerprint = this.energyQuanta;
				for(int i = 0; i < getRecipeCount(); i++) {
					if(runtimeMaterialEligible[i] && this.hasLanePower()) this.scheduleMachineTransition(now, TASK_ASSEMBLER_LANE, i);
				}
			}
			if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ASSEMBLER_BATTERY, TASK_SLOT_BATTERY);
			return;
		}
		if(taskType != TASK_ASSEMBLER_LANE || taskSlot < 0 || taskSlot >= getRecipeCount()) return;
		if(runtimeLaneTick[taskSlot] == now) return;
		runtimeLaneTick[taskSlot] = now;
		this.beginRuntimeAccounting(now);
		long oldPower = this.energyQuanta;
		int oldProgress = this.progress[taskSlot];
		int oldMaxProgress = this.maxProgress[taskSlot];
		boolean canRun = runtimeMaterialEligible[taskSlot] && this.hasLanePower();
		int completion = this.getProcessTime(taskSlot) * this.speed / 100;
		if(canRun && this.progress[taskSlot] + 1 >= completion) canRun = this.canProcess(taskSlot);
		if(canRun) {
			this.isProgressing = true;
			runtimeEnergyMutation = true;
			try { this.process(taskSlot); } finally { runtimeEnergyMutation = false; }
			this.scheduleMachineTransition(now + 1L, TASK_ASSEMBLER_LANE, taskSlot);
		} else {
			this.progress[taskSlot] = 0;
		}
		if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ASSEMBLER_BATTERY, TASK_SLOT_BATTERY);
		if(oldPower != this.energyQuanta || oldProgress != this.progress[taskSlot] || oldMaxProgress != this.maxProgress[taskSlot]) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.runtimePowerFingerprint = this.energyQuanta;
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			boolean inventoryChanged = this.updateRuntimeItemFingerprints();
			if(inventoryChanged) {
				this.markDirty();
				this.markNetworkDirty();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
				return;
			}
			boolean changed = false;
			for(int i = 0; i < getRecipeCount(); i++) {
				int output = getCachedSlotIndicesFromIndex(i)[2];
				if(slots[output] != null || needsTemplateSwitch[i]) unloadItems(i);
				if(!runtimeHasRequiredItems[i]) changed |= loadItems(i);
			}
			if(changed) {
				this.markDirty();
				this.markNetworkDirty();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			}
			this.onMachineRuntimeMaintenance(cadence);
			return;
		}
		if(cadence != 20) return;
		boolean settingsChanged = this.refreshRuntimeSettings(true);
		long recipeGeneration = AssemblerRecipes.recipeGeneration;
		boolean recipeChanged = recipeGeneration != this.runtimeRecipeGeneration;
		boolean changed = recipeChanged || settingsChanged || this.energyQuanta != this.runtimePowerFingerprint;
		if(recipeChanged) this.runtimeRecipeGeneration = recipeGeneration;
		if(changed) this.markMachineDirty((recipeChanged ? MachineDirtyCause.RECIPE : 0) | MachineDirtyCause.CONFIGURATION | MachineDirtyCause.ENERGY);
		this.networkPackNTIfDirty(150);
		this.onMachineRuntimeMaintenance(cadence);
	}

	/** Recomputes family-specific upgrades/configuration; return true when effective values changed. */
	protected boolean refreshRuntimeSettings(boolean contentAware) { return false; }

	/** Family hook for bounded connection and fluid-output maintenance. */
	protected void onMachineRuntimeMaintenance(int cadence) { }

	private boolean beginRuntimeAccounting(long now) {
		if(runtimeLastAccountingTick != now) {
			runtimeLastAccountingTick = now;
			this.isProgressing = false;
		}
		return this.chargeRuntimeEnergy(now);
	}

	private boolean chargeRuntimeEnergy(long now) {
		if(runtimeLastChargeTick == now) return false;
		runtimeLastChargeTick = now;
		long before = this.energyQuanta;
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, getPowerSlot(), energyQuanta, getEnergyCapacityQuanta())); }
		finally { runtimeEnergyMutation = false; }
		return before != this.energyQuanta;
	}

	private boolean hasRuntimeBatteryWork() {
		if(this.energyQuanta >= this.getEnergyCapacityQuanta()) return false;
		int slot = this.getPowerSlot();
		if(slot < 0 || slot >= slots.length || slots[slot] == null) return false;
		if(slots[slot].getItem() == ModItems.battery_creative || slots[slot].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[slot].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[slot].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[slot]) > 0;
	}

	private boolean hasLanePower() {
		return this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts);
	}

	private boolean updateRuntimeItemFingerprints() {
		boolean changed = false;
		for(int i = 0; i < slots.length; i++) {
			int fingerprint = runtimeItemFingerprint(i);
			if(runtimeFingerprintsInitialized && runtimeItemFingerprints[i] != fingerprint) changed = true;
			runtimeItemFingerprints[i] = fingerprint;
		}
		runtimeFingerprintsInitialized = true;
		return changed;
	}

	private int runtimeItemFingerprint(int index) {
		ItemStack stack = slots[index];
		if(stack == null) return 0;
		int hash = System.identityHashCode(stack.getItem());
		hash = 31 * hash + stack.getItemDamage();
		hash = 31 * hash + stack.stackSize;
		return 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
	}

	protected boolean canProcess(int index) {
		int template = getTemplateIndex(index);
		runtimeMaterialEligible[index] = false;
		runtimeHasRequiredItems[index] = false;
		if(slots[template] == null || slots[template].getItem() != ModItems.assembly_template) return false;
		this.resolveRecipe(index);
		AStack[] recipe = this.cachedRecipes[index];
		if(recipe == null) return false;
		runtimeHasRequiredItems[index] = hasRequiredItems(recipe, index);
		if(!runtimeHasRequiredItems[index] || !hasSpaceForItems(this.cachedOutputs[index], index)) return false;
		runtimeMaterialEligible[index] = true;
		return this.hasLanePower();
	}

	protected boolean hasAssemblerInputs(int index) {

		int template = getTemplateIndex(index);

		if(slots[template] == null || slots[template].getItem() != ModItems.assembly_template)
			return false;

		this.resolveRecipe(index);
		AStack[] recipe = this.cachedRecipes[index];

		if(recipe == null)
			return false;

		runtimeHasRequiredItems[index] = hasRequiredItems(recipe, index);
		return runtimeHasRequiredItems[index];
	}

	protected boolean hasValidProcessInputs(int index) {
		if(!this.hasAssemblerInputs(index)) return false;
		return this.hasAssemblerOutputSpace(index);
	}

	protected boolean hasAssemblerOutputSpace(int index) {
		this.resolveRecipe(index);
		if(this.cachedRecipes[index] == null) return false;
		return hasSpaceForItems(this.cachedOutputs[index], index);
	}

	protected int getProcessTime(int index) {
		this.resolveRecipe(index);
		return this.cachedProcessTimes[index];
	}

	private boolean hasRequiredItems(AStack[] recipe, int index) {
		int[] indices = getCachedSlotIndicesFromIndex(index);
		return InventoryUtil.doesArrayHaveIngredients(slots, indices[0], indices[1], recipe);
	}

	private boolean hasSpaceForItems(ItemStack recipe, int index) {
		int[] indices = getCachedSlotIndicesFromIndex(index);
		return InventoryUtil.doesArrayHaveSpace(slots, indices[2], indices[2], this.cachedOutputArrays[index]);
	}

	protected void process(int index) {

		this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));
		this.progress[index]++;

		//if(slots[0] != null && slots[0].getItem() == ModItems.meteorite_sword_alloyed)
		//	slots[0] = new ItemStack(ModItems.meteorite_sword_machined); //fisfndmoivndlmgindgifgjfdnblfm

		this.resolveRecipe(index);
		AStack[] recipe = this.cachedRecipes[index];
		ItemStack output = this.cachedOutputs[index];
		int time = this.cachedProcessTimes[index];

		this.maxProgress[index] = time * this.speed / 100;

		if(this.progress[index] >= this.maxProgress[index]) {
			consumeItems(recipe, index);
			produceItems(output, index);
			this.progress[index] = 0;
			this.needsTemplateSwitch[index] = true;
			this.markDirty();
			this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
		}
	}

	private void consumeItems(AStack[] recipe, int index) {

		int[] indices = getCachedSlotIndicesFromIndex(index);

		for(AStack in : recipe) {
			if(in != null)
				InventoryUtil.tryConsumeAStack(slots, indices[0], indices[1], in);
		}
	}

	private void produceItems(ItemStack out, int index) {

		int[] indices = getCachedSlotIndicesFromIndex(index);

		if(out != null) {
			InventoryUtil.tryAddItemToInventory(slots, indices[2], indices[2], out.copy());
		}
	}

	protected boolean loadItems(int index) {
		boolean changed = false;

		int template = getTemplateIndex(index);

		DirPos[] positions = getInputPositions();
		int[] indices = getCachedSlotIndicesFromIndex(index);

		for(DirPos coord : positions) {

			TileEntity te = worldObj.getTileEntity(coord.getX(), coord.getY(), coord.getZ());

			if(te instanceof IInventory) {

				IInventory inv = (IInventory) te;
				ISidedInventory sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
				int[] access = sided != null ? sided.getAccessibleSlotsFromSide(coord.getDir().ordinal()) : null;
				boolean templateCrate = te instanceof TileEntityCrateTemplate;

				if(templateCrate && slots[template] == null) {

					for(int i = 0; i < (access != null ? access.length : inv.getSizeInventory()); i++) {
						int slot = access != null ? access[i] : i;
						ItemStack stack = inv.getStackInSlot(slot);

						if(stack != null && stack.getItem() == ModItems.assembly_template && (sided == null || sided.canExtractItem(slot, stack, 0))) {
							slots[template] = stack.copy();
							sided.setInventorySlotContents(slot, null);
							inv.markDirty();
							this.needsTemplateSwitch[index] = false;
							changed = true;
							break;
						}
					}
				}

				boolean noTemplate = slots[template] == null || slots[template].getItem() != ModItems.assembly_template;

				if(!noTemplate) {

					this.resolveRecipe(index);
					AStack[] recipe = this.cachedRecipes[index];

					if(recipe != null) {

						for(AStack ingredient : recipe) {

							int tracker = 0;

							outer: while(!InventoryUtil.doesArrayHaveIngredients(slots, indices[0], indices[1], ingredient)) {

								if(tracker++ > 10) break;

								boolean found = false;

								for(int i = 0; i < (access != null ? access.length : inv.getSizeInventory()); i++) {

									int slot = access != null ? access[i] : i;
									ItemStack stack = inv.getStackInSlot(slot);
									if(ingredient.matchesRecipe(stack, true) && (sided == null || sided.canExtractItem(slot, stack, 0))) {
										found = true;

										for(int j = indices[0]; j <= indices[1]; j++) {

											if(slots[j] != null && slots[j].stackSize < slots[j].getMaxStackSize() & InventoryUtil.doesStackDataMatch(slots[j], stack)) {
												inv.decrStackSize(slot, 1);
												inv.markDirty();
												slots[j].stackSize++;
												changed = true;
												continue outer;
											}
										}

										for(int j = indices[0]; j <= indices[1]; j++) {

											if(slots[j] == null) {
												slots[j] = stack.copy();
												slots[j].stackSize = 1;
												inv.decrStackSize(slot, 1);
												inv.markDirty();
												changed = true;
												continue outer;
											}
										}
									}
								}

								if(!found) break outer;
							}
						}
					}
				}
			}
		}
		return changed;
	}

	protected void unloadItems(int index) {

		DirPos[] positions = getOutputPositions();
		int[] indices = getCachedSlotIndicesFromIndex(index);

		for(DirPos coord : positions) {

			TileEntity te = worldObj.getTileEntity(coord.getX(), coord.getY(), coord.getZ());

			if(te instanceof IInventory) {

				IInventory inv = (IInventory) te;
				ISidedInventory sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
				int[] access = sided != null ? sided.getAccessibleSlotsFromSide(coord.getDir().ordinal()) : null;

				int i = indices[2];
				ItemStack out = slots[i];

				int template = getTemplateIndex(index);
				if(this.needsTemplateSwitch[index] && te instanceof TileEntityCrateTemplate && slots[template] != null) {
					out = slots[template];
					i = template;
				}

				if(out != null) {

					for(int j = 0; j < (access != null ? access.length : inv.getSizeInventory()); j++) {

						int slot = access != null ? access[j] : j;

						if(!(sided != null ? sided.canInsertItem(slot, out, coord.getDir().ordinal()) : inv.isItemValidForSlot(slot, out)))
							continue;

						ItemStack target = inv.getStackInSlot(slot);

						if(InventoryUtil.doesStackDataMatch(out, target) && target.stackSize < target.getMaxStackSize() && target.stackSize < inv.getInventoryStackLimit()) {
							this.decrStackSize(i, 1);
							target.stackSize++;
							inv.markDirty();
							return;
						}
					}

					for(int j = 0; j < (access != null ? access.length : inv.getSizeInventory()); j++) {

						int slot = access != null ? access[j] : j;

						if(!inv.isItemValidForSlot(slot, out))
							continue;

						if(inv.getStackInSlot(slot) == null && (sided != null ? sided.canInsertItem(slot, out, coord.getDir().ordinal()) : inv.isItemValidForSlot(slot, out))) {
							ItemStack copy = out.copy();
							copy.stackSize = 1;
							inv.setInventorySlotContents(slot, copy);
							inv.markDirty();
							this.decrStackSize(i, 1);
							return;
						}
					}
				}
			}
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		if(nbt.hasKey("progress")) this.progress = nbt.getIntArray("progress");
		if(nbt.hasKey("maxProgress")) this.maxProgress = nbt.getIntArray("maxProgress");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setIntArray("progress", progress);
		nbt.setIntArray("maxProgress", maxProgress);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	private int[] getCachedSlotIndicesFromIndex(int index) {
		int[] indices = this.cachedSlotIndices[index];
		if(indices == null) {
			indices = getSlotIndicesFromIndex(index);
			this.cachedSlotIndices[index] = indices;
		}
		return indices;
	}

	private void resolveRecipe(int index) {
		ItemStack template = this.slots[getTemplateIndex(index)];
		Item item = template == null ? null : template.getItem();
		int meta = template == null ? 0 : template.getItemDamage();
		int nbtHash = template == null || template.getTagCompound() == null ? 0 : template.getTagCompound().hashCode();
		if(this.cachedTemplateStacks[index] == template && this.cachedTemplateItems[index] == item && this.cachedTemplateMeta[index] == meta && this.cachedTemplateNbtHash[index] == nbtHash && this.cachedRecipeGeneration[index] == AssemblerRecipes.recipeGeneration) return;

		this.cachedTemplateStacks[index] = template;
		this.cachedTemplateItems[index] = item;
		this.cachedTemplateMeta[index] = meta;
		this.cachedTemplateNbtHash[index] = nbtHash;
		this.cachedRecipeGeneration[index] = AssemblerRecipes.recipeGeneration;
		this.cachedRecipes[index] = null;
		this.cachedOutputs[index] = null;
		this.cachedOutputArrays[index] = null;
		this.cachedProcessTimes[index] = 0;

		if(template == null || template.getItem() != ModItems.assembly_template) return;
		List<AStack> recipe = AssemblerRecipes.getRecipeFromTempate(template);
		if(recipe == null) return;
		this.cachedRecipes[index] = recipe.toArray(new AStack[recipe.size()]);
		this.cachedOutputs[index] = AssemblerRecipes.getOutputFromTempate(template);
		this.cachedOutputArrays[index] = new ItemStack[] { this.cachedOutputs[index] };
		this.cachedProcessTimes[index] = ItemAssemblyTemplate.getProcessTime(template);
	}

	public abstract int getRecipeCount();
	public abstract int getTemplateIndex(int index);

	/**
	 * @param index
	 * @return A size 3 int array containing min input, max input and output indices in that order.
	 */
	public abstract int[] getSlotIndicesFromIndex(int index);
	public abstract DirPos[] getInputPositions();
	public abstract DirPos[] getOutputPositions();
	public abstract int getPowerSlot();
}
