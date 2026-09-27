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
	private final boolean[] runtimeLaneActive;
	private final boolean[] runtimeHasRequiredItems;
	private final int[] runtimeItemFingerprints;
	private long runtimeRecipeGeneration = -1L;
	private long runtimePowerFingerprint;
	private long runtimeLastAccountingTick = Long.MIN_VALUE;
	private long runtimeLastChargeTick = Long.MIN_VALUE;
	private boolean runtimeFingerprintsInitialized;
	private boolean runtimeEnergyMutation;
	private long runtimeClientProgressTick;

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
		runtimeLaneActive = new boolean[count];
		runtimeHasRequiredItems = new boolean[count];
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
		long now = worldObj.getTotalWorldTime();
		if(runtimeLastAccountingTick == Long.MIN_VALUE) runtimeLastAccountingTick = now;
		else this.settleLanesThrough(now - 1L);
		boolean[] previouslyActive = runtimeLaneActive.clone();
		this.refreshRuntimeSettings(false);
		long recipeGeneration = AssemblerRecipes.recipeGeneration;
		if(recipeGeneration != this.runtimeRecipeGeneration) this.runtimeRecipeGeneration = recipeGeneration;
		for(int i = 0; i < getRecipeCount(); i++) {
			boolean eligible = this.canProcess(i);
			if(eligible) maxProgress[i] = Math.max(1, getProcessTime(i) * speed / 100);
			runtimeLaneActive[i] = eligible && previouslyActive[i];
			if(!eligible) this.progress[i] = 0;
		}
		this.settleLanesThrough(now);
		this.isProgressing = false;
		for(int i = 0; i < getRecipeCount(); i++) {
			runtimeLaneActive[i] = this.canProcess(i);
			if(runtimeLaneActive[i] && progress[i] > 0) this.isProgressing = true;
		}
		this.scheduleLaneBoundaries(now);
		this.runtimePowerFingerprint = this.energyQuanta;
		this.runtimeRecipeGeneration = recipeGeneration;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_ASSEMBLER_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleLanesThrough(now);
			if(this.chargeRuntimeEnergy(now)) { this.markDirty(); this.markNetworkDirty(); }
			long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
			boolean resourcesAvailable = cost <= 0L || energyQuanta >= cost;
			resourcesAvailable &= this.additionalResourceFullTicks(1) > 0L;
			for(int i = 0; i < getRecipeCount(); i++) if(runtimeMaterialEligible[i] && resourcesAvailable) runtimeLaneActive[i] = true;
			this.scheduleLaneBoundaries(now);
			this.runtimePowerFingerprint = this.energyQuanta;
			return;
		} else if(taskType == TASK_ASSEMBLER_LANE && taskSlot >= 0 && taskSlot < getRecipeCount()) this.settleLanesThrough(now);
		else return;
		for(int i = 0; i < getRecipeCount(); i++) runtimeLaneActive[i] = this.canProcess(i);
		this.scheduleLaneBoundaries(now);
		this.runtimePowerFingerprint = this.energyQuanta;
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			this.settleLanesThrough(worldObj.getTotalWorldTime());
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

	/** Resource rates outside the energy buffer, for factories with per-tick water or steam use. */
	protected long additionalResourceFullTicks(int activeLanes) { return Long.MAX_VALUE; }
	protected void consumeAdditionalResources(long ticks, int activeLanes) { }

	private void settleLanesThrough(long target) {
		if(runtimeLastAccountingTick == Long.MIN_VALUE || target <= runtimeLastAccountingTick) return;
		long cursor = runtimeLastAccountingTick;
		long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
		boolean changed = false;
		this.isProgressing = false;
		runtimeEnergyMutation = true;
		try {
			while(cursor < target) {
				int active = 0;
				int nearestCompletion = Integer.MAX_VALUE;
				for(int i = 0; i < getRecipeCount(); i++) if(runtimeLaneActive[i] && runtimeMaterialEligible[i]) {
					active++;
					nearestCompletion = Math.min(nearestCompletion, Math.max(1, maxProgress[i] - progress[i]));
				}
				if(active == 0) { cursor = target; break; }
				long wholeTicks = Math.min(target - cursor, (long) nearestCompletion - 1L);
				if(cost > 0L) wholeTicks = Math.min(wholeTicks, energyQuanta / cost / active);
				wholeTicks = Math.min(wholeTicks, this.additionalResourceFullTicks(active));
				if(wholeTicks > 0L) {
					for(int i = 0; i < getRecipeCount(); i++) if(runtimeLaneActive[i] && runtimeMaterialEligible[i]) progress[i] += (int) wholeTicks;
					if(cost > 0L) this.setStoredEnergyQuanta(energyQuanta - wholeTicks * cost * active);
					this.consumeAdditionalResources(wholeTicks, active);
					cursor += wholeTicks;
					this.isProgressing = true;
					changed = true;
					if(cursor >= target) break;
				}
				cursor++;
				boolean sharedMutation = false;
				for(int i = 0; i < getRecipeCount(); i++) {
					if(!runtimeLaneActive[i]) continue;
					if(sharedMutation) runtimeMaterialEligible[i] = this.canProcess(i);
					if(!runtimeMaterialEligible[i]) {
						progress[i] = 0;
						runtimeLaneActive[i] = false;
						changed = true;
						continue;
					}
					if(cost > 0L && energyQuanta < cost || this.additionalResourceFullTicks(1) <= 0L) {
						progress[i] = 0;
						runtimeLaneActive[i] = false;
						changed = true;
						continue;
					}
					int duration = maxProgress[i];
					if(progress[i] + 1 >= duration) {
						this.process(i);
						sharedMutation = true;
					} else {
						this.setStoredEnergyQuanta(energyQuanta - cost);
						this.consumeAdditionalResources(1L, 1);
						progress[i]++;
					}
					this.isProgressing = true;
					changed = true;
				}
				if(sharedMutation) for(int i = 0; i < getRecipeCount(); i++) runtimeLaneActive[i] = this.canProcess(i);
			}
		} finally { runtimeEnergyMutation = false; }
		runtimeLastAccountingTick = cursor;
		if(changed) { this.markDirty(); this.markNetworkDirty(); }
	}

	private void scheduleLaneBoundaries(long now) {
		int active = 0;
		int nearestCompletion = Integer.MAX_VALUE;
		for(int i = 0; i < getRecipeCount(); i++) {
			this.cancelMachineTransition(TASK_ASSEMBLER_LANE, i);
			if(runtimeLaneActive[i]) {
				active++;
				nearestCompletion = Math.min(nearestCompletion, Math.max(1, maxProgress[i] - progress[i]));
			}
		}
		if(active > 0) {
			long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
			long powerBoundary = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost / active + 1L;
			long availableResourceTicks = this.additionalResourceFullTicks(active);
			long resourceBoundary = availableResourceTicks == Long.MAX_VALUE ? Long.MAX_VALUE : availableResourceTicks + 1L;
			long due = now + Math.max(1L, Math.min(nearestCompletion, Math.min(powerBoundary, resourceBoundary)));
			for(int i = 0; i < getRecipeCount(); i++) if(runtimeLaneActive[i]) this.scheduleMachineTransition(due, TASK_ASSEMBLER_LANE, i);
		}
		if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ASSEMBLER_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_ASSEMBLER_BATTERY, TASK_SLOT_BATTERY);
	}

	public boolean isRuntimeLaneActive(int lane) { return runtimeLaneActive[lane]; }
	public void setClientLaneState(int lane, boolean active) { runtimeLaneActive[lane] = active; }
	public void setClientProgressTick(long tick) { runtimeClientProgressTick = tick; }
	public int getDisplayedProgress(int lane) {
		int value = progress[lane];
		if(worldObj != null && worldObj.isRemote && runtimeLaneActive[lane]) value = (int) Math.min(maxProgress[lane], value + Math.max(0L, worldObj.getTotalWorldTime() - runtimeClientProgressTick));
		return value;
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
		if(worldObj != null && !worldObj.isRemote) this.settleLanesThrough(worldObj.getTotalWorldTime() - 1L);
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
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.settleLanesThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(worldObj != null && !worldObj.isRemote) this.settleLanesThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(com.hbm.inventory.fluid.tank.FluidTank tank) {
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.settleLanesThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleLanesThrough(worldObj.getTotalWorldTime() - 1L);
		runtimeLastAccountingTick = Long.MIN_VALUE;
		java.util.Arrays.fill(runtimeLaneActive, false);
		super.onChunkUnload();
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
