package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ChemplantRecipes;
import com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.InventoryUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidUser;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Base class for single and multi chemplants.
 * Most stuff should be handled by this class automatically, given the slots and indices are defined correctly
 * Does not sync automatically, nor handle upgrades
 * Slot indices are mostly free game, but battery has to be slot 0
 * Tanks follow the order R1(I1, I2, O1, O2), R2(I1, I2, O1, O2) ...
 * @author hbm
 */
public abstract class TileEntityMachineChemplantBase extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidUser, IGUIProvider {

	public long energyQuanta;
	public int[] progress;
	public int[] maxProgress;
	public boolean isProgressing;
	private final ItemStack[] cachedTemplateStacks;
	private final int[] cachedTemplateMeta;
	private final ChemRecipe[] cachedRecipes;
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

	private static final int TASK_CHEMPLANT_LANE = 1;
	private static final int TASK_CHEMPLANT_BATTERY = 2;
	private static final int TASK_SLOT_BATTERY = -1;

	public FluidTank[] tanks;

	long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(100L);
	int speed = 100;

	public TileEntityMachineChemplantBase(int scount) {
		super(scount);

		int count = this.getRecipeCount();

		progress = new int[count];
		maxProgress = new int[count];
		cachedTemplateStacks = new ItemStack[count];
		cachedTemplateMeta = new int[count];
		cachedRecipes = new ChemRecipe[count];
		cachedSlotIndices = new int[count][];
		runtimeMaterialEligible = new boolean[count];
		runtimeHasRequiredItems = new boolean[count];
		runtimeLaneTick = new long[count];
		java.util.Arrays.fill(runtimeLaneTick, Long.MIN_VALUE);
		runtimeItemFingerprints = new int[scount];

		tanks = new FluidTank[4 * count];
		for(int i = 0; i < 4 * count; i++) {
			tanks[i] = new FluidTank(Fluids.NONE, getTankCapacity());
			this.trackMachineFluidTank(tanks[i]);
		}
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
		long recipeGeneration = ChemplantRecipes.recipeGeneration;
		this.runtimeRecipeGeneration = recipeGeneration;
		long now = worldObj.getTotalWorldTime();
		this.isProgressing = false;
		if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_CHEMPLANT_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_CHEMPLANT_BATTERY, TASK_SLOT_BATTERY);
		for(int i = 0; i < getRecipeCount(); i++) {
			boolean eligible = this.canProcess(i);
			if(eligible && progress[i] > 0) this.isProgressing = true;
			if(eligible) this.scheduleMachineTransition(now + 1L, TASK_CHEMPLANT_LANE, i);
			else {
				this.cancelMachineTransition(TASK_CHEMPLANT_LANE, i);
				this.progress[i] = 0;
			}
		}
		this.runtimePowerFingerprint = this.energyQuanta;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_CHEMPLANT_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			boolean charged = this.beginRuntimeAccounting(now);
			if(charged) {
				this.markDirty();
				this.markNetworkDirty();
				this.runtimePowerFingerprint = this.energyQuanta;
				for(int i = 0; i < getRecipeCount(); i++) {
					if(runtimeMaterialEligible[i] && this.hasLanePower()) this.scheduleMachineTransition(now, TASK_CHEMPLANT_LANE, i);
				}
			}
			if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_CHEMPLANT_BATTERY, TASK_SLOT_BATTERY);
			return;
		}
		if(taskType != TASK_CHEMPLANT_LANE || taskSlot < 0 || taskSlot >= getRecipeCount()) return;
		if(runtimeLaneTick[taskSlot] == now) return;
		runtimeLaneTick[taskSlot] = now;
		this.beginRuntimeAccounting(now);
		long oldPower = this.energyQuanta;
		int oldProgress = this.progress[taskSlot];
		int oldMaxProgress = this.maxProgress[taskSlot];
		boolean canRun = runtimeMaterialEligible[taskSlot] && this.hasLanePower();
		ChemRecipe recipe = this.resolveRecipe(taskSlot);
		int completion = recipe != null ? recipe.getDuration() * this.speed / 100 : 1;
		if(completion <= 0) completion = 1;
		if(canRun && this.progress[taskSlot] + 1 >= completion) canRun = this.canProcess(taskSlot);
		boolean oxygenBlocked = canRun && recipe != null && recipe.oxygenConsumption > 0 && !this.breatheAir(recipe.oxygenConsumption);
		if(oxygenBlocked) canRun = false;
		if(canRun) {
			this.isProgressing = true;
			runtimeEnergyMutation = true;
			try { this.process(taskSlot); } finally { runtimeEnergyMutation = false; }
			this.scheduleMachineTransition(now + 1L, TASK_CHEMPLANT_LANE, taskSlot);
		} else {
			this.progress[taskSlot] = 0;
			if(oxygenBlocked && runtimeMaterialEligible[taskSlot] && this.hasLanePower()) this.scheduleMachineTransition(now + 20L, TASK_CHEMPLANT_LANE, taskSlot);
		}
		if(this.hasRuntimeBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_CHEMPLANT_BATTERY, TASK_SLOT_BATTERY);
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
			boolean transferred = false;
			for(int i = 0; i < getRecipeCount(); i++) {
				int[] indices = getCachedSlotIndicesFromIndex(i);
				boolean hasOutput = false;
				for(int slot = indices[2]; slot <= indices[3]; slot++) if(slots[slot] != null) { hasOutput = true; break; }
				if(hasOutput) unloadItems(i);
				if(!runtimeHasRequiredItems[i]) transferred |= loadItems(i);
			}
			if(transferred) {
				this.markDirty();
				this.markNetworkDirty();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
			}
			this.onMachineRuntimeMaintenance(cadence);
			return;
		}
		if(cadence != 20) return;
		boolean settingsChanged = this.refreshRuntimeSettings(true);
		boolean recipeChanged = ChemplantRecipes.recipeGeneration != this.runtimeRecipeGeneration;
		if(recipeChanged) this.runtimeRecipeGeneration = ChemplantRecipes.recipeGeneration;
		boolean energyChanged = this.energyQuanta != this.runtimePowerFingerprint;
		if(settingsChanged || recipeChanged || energyChanged) {
			this.markMachineDirty((settingsChanged ? MachineDirtyCause.CONFIGURATION : 0) | (recipeChanged ? MachineDirtyCause.RECIPE : 0) | (energyChanged ? MachineDirtyCause.ENERGY : 0));
		}
		this.networkPackNTIfDirty(150);
		this.onMachineRuntimeMaintenance(cadence);
	}

	/** Recomputes family-specific upgrades/configuration; return true when effective values changed. */
	protected boolean refreshRuntimeSettings(boolean contentAware) { return false; }

	/** Family hook for bounded connection and fluid-output maintenance. */
	protected void onMachineRuntimeMaintenance(int cadence) { }

	private boolean hasRequiredItemsForTransfer(int index) {
		ChemRecipe recipe = this.resolveRecipe(index);
		return recipe != null && this.hasRequiredItems(recipe, index);
	}

	private boolean beginRuntimeAccounting(long now) {
		if(runtimeLastAccountingTick != now) {
			runtimeLastAccountingTick = now;
			this.isProgressing = false;
		}
		if(runtimeLastChargeTick == now) return false;
		runtimeLastChargeTick = now;
		long before = this.energyQuanta;
		runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, this.getEnergyCapacityQuanta())); }
		finally { runtimeEnergyMutation = false; }
		return before != this.energyQuanta;
	}

	private boolean hasRuntimeBatteryWork() {
		if(this.energyQuanta >= this.getEnergyCapacityQuanta() || slots.length == 0 || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasLanePower() {
		return this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts);
	}

	private boolean updateRuntimeItemFingerprints() {
		boolean changed = false;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int fingerprint = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
			if(runtimeFingerprintsInitialized && runtimeItemFingerprints[i] != fingerprint) changed = true;
			runtimeItemFingerprints[i] = fingerprint;
		}
		runtimeFingerprintsInitialized = true;
		return changed;
	}

	protected boolean canProcess(int index) {
		return this.canProcessResources(index) && this.hasLanePower();
	}

	private boolean canProcessResources(int index) {
		runtimeMaterialEligible[index] = false;
		runtimeHasRequiredItems[index] = false;

		int template = getTemplateIndex(index);

		if(slots[template] == null || slots[template].getItem() != ModItems.chemistry_template)
			return false;

		ChemRecipe recipe = this.resolveRecipe(index);

		if(recipe == null)
			return false;

		setupTanks(recipe, index);

		if(!hasRequiredFluids(recipe, index)) return false;
		if(!hasSpaceForFluids(recipe, index)) return false;
		runtimeHasRequiredItems[index] = hasRequiredItems(recipe, index);
		if(!runtimeHasRequiredItems[index]) return false;
		if(!hasSpaceForItems(recipe, index)) return false;

		runtimeMaterialEligible[index] = true;
		return true;
	}

	private void setupTanks(ChemRecipe recipe, int index) {
		if(recipe.inputFluids[0] != null) tanks[index * 4].withPressure(recipe.inputFluids[0].pressure).setTankType(recipe.inputFluids[0].type);		else tanks[index * 4].setTankType(Fluids.NONE);
		if(recipe.inputFluids[1] != null) tanks[index * 4 + 1].withPressure(recipe.inputFluids[1].pressure).setTankType(recipe.inputFluids[1].type);	else tanks[index * 4 + 1].setTankType(Fluids.NONE);
		if(recipe.outputFluids[0] != null) tanks[index * 4 + 2].withPressure(recipe.outputFluids[0].pressure).setTankType(recipe.outputFluids[0].type);	else tanks[index * 4 + 2].setTankType(Fluids.NONE);
		if(recipe.outputFluids[1] != null) tanks[index * 4 + 3].withPressure(recipe.outputFluids[1].pressure).setTankType(recipe.outputFluids[1].type);	else tanks[index * 4 + 3].setTankType(Fluids.NONE);
	}

	private boolean hasRequiredFluids(ChemRecipe recipe, int index) {
		if(recipe.inputFluids[0] != null && tanks[index * 4].getFill() < recipe.inputFluids[0].fill) return false;
		if(recipe.inputFluids[1] != null && tanks[index * 4 + 1].getFill() < recipe.inputFluids[1].fill) return false;
		return true;
	}

	private boolean hasSpaceForFluids(ChemRecipe recipe, int index) {
		if(recipe.outputFluids[0] != null && tanks[index * 4 + 2].getFill() + recipe.outputFluids[0].fill > tanks[index * 4 + 2].getMaxFill()) return false;
		if(recipe.outputFluids[1] != null && tanks[index * 4 + 3].getFill() + recipe.outputFluids[1].fill > tanks[index * 4 + 3].getMaxFill()) return false;
		return true;
	}

	private boolean hasRequiredItems(ChemRecipe recipe, int index) {
		int[] indices = getCachedSlotIndicesFromIndex(index);
		return InventoryUtil.doesArrayHaveIngredients(slots, indices[0], indices[1], recipe.inputs);
	}

	private boolean hasSpaceForItems(ChemRecipe recipe, int index) {
		int[] indices = getCachedSlotIndicesFromIndex(index);
		return InventoryUtil.doesArrayHaveSpace(slots, indices[2], indices[3], recipe.outputs);
	}

	protected void process(int index) {

		this.setStoredEnergyQuanta(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));
		this.progress[index]++;

		//if(slots[0] != null && slots[0].getItem() == ModItems.meteorite_sword_machined)
		//	slots[0] = new ItemStack(ModItems.meteorite_sword_treated); //fisfndmoivndlmgindgifgjfdnblfm

		ChemRecipe recipe = this.resolveRecipe(index);

		this.maxProgress[index] = recipe.getDuration() * this.speed / 100;

		if(maxProgress[index] <= 0) maxProgress[index] = 1;

		if(this.progress[index] >= this.maxProgress[index]) {
			consumeFluids(recipe, index);
			produceFluids(recipe, index);
			consumeItems(recipe, index);
			produceItems(recipe, index);
			this.progress[index] = 0;
			this.markDirty();
			this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
		}
	}

	private void consumeFluids(ChemRecipe recipe, int index) {
		if(recipe.inputFluids[0] != null) tanks[index * 4].setFill(tanks[index * 4].getFill() - recipe.inputFluids[0].fill);
		if(recipe.inputFluids[1] != null) tanks[index * 4 + 1].setFill(tanks[index * 4 + 1].getFill() - recipe.inputFluids[1].fill);
	}

	private void produceFluids(ChemRecipe recipe, int index) {
		if(recipe.outputFluids[0] != null) tanks[index * 4 + 2].setFill(tanks[index * 4 + 2].getFill() + recipe.outputFluids[0].fill);
		if(recipe.outputFluids[1] != null) tanks[index * 4 + 3].setFill(tanks[index * 4 + 3].getFill() + recipe.outputFluids[1].fill);
	}

	private void consumeItems(ChemRecipe recipe, int index) {

		int[] indices = getCachedSlotIndicesFromIndex(index);

		for(AStack in : recipe.inputs) {
			if(in != null)
				InventoryUtil.tryConsumeAStack(slots, indices[0], indices[1], in);
		}
	}

	private void produceItems(ChemRecipe recipe, int index) {

		int[] indices = getCachedSlotIndicesFromIndex(index);

		for(ItemStack out : recipe.outputs) {
			if(out != null)
				InventoryUtil.tryAddItemToInventory(slots, indices[2], indices[3], out.copy());
		}
	}

	private boolean loadItems(int index) {

		int template = getTemplateIndex(index);
		if(slots[template] == null || slots[template].getItem() != ModItems.chemistry_template)
			return false;

		ChemRecipe recipe = this.resolveRecipe(index);
		boolean changed = false;

		if(recipe != null) {

			DirPos[] positions = getInputPositions();
			int[] indices = getCachedSlotIndicesFromIndex(index);

			for(DirPos coord : positions) {

				TileEntity te = worldObj.getTileEntity(coord.getX(), coord.getY(), coord.getZ());

				if(te instanceof IInventory) {

					IInventory inv = (IInventory) te;
					ISidedInventory sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
					int[] access = sided != null ? sided.getAccessibleSlotsFromSide(coord.getDir().ordinal()) : null;

					for(AStack ingredient : recipe.inputs) {

						outer:
						while(!InventoryUtil.doesArrayHaveIngredients(slots, indices[0], indices[1], ingredient)) {

							boolean found = false;

							for(int i = 0; i < (access != null ? access.length : inv.getSizeInventory()); i++) {

								int slot = access != null ? access[i] : i;
								ItemStack stack = inv.getStackInSlot(slot);
								if(ingredient.matchesRecipe(stack, true) && (sided == null || sided.canExtractItem(slot, stack, 0))) {

									for(int j = indices[0]; j <= indices[1]; j++) {

						if(slots[j] != null && slots[j].stackSize < slots[j].getMaxStackSize() & InventoryUtil.doesStackDataMatch(slots[j], stack)) {
							inv.decrStackSize(slot, 1);
							slots[j].stackSize++;
							changed = true;
							inv.markDirty();
							continue outer;
										}
									}

									for(int j = indices[0]; j <= indices[1]; j++) {

										if(slots[j] == null) {
							slots[j] = stack.copy();
							slots[j].stackSize = 1;
							inv.decrStackSize(slot, 1);
							changed = true;
							inv.markDirty();
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
		return changed;
	}

	private void unloadItems(int index) {

		DirPos[] positions = getOutputPositions();
		int[] indices = getCachedSlotIndicesFromIndex(index);

		for(DirPos coord : positions) {

			TileEntity te = worldObj.getTileEntity(coord.getX(), coord.getY(), coord.getZ());

			if(te instanceof IInventory) {

				IInventory inv = (IInventory) te;
				ISidedInventory sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
				int[] access = sided != null ? sided.getAccessibleSlotsFromSide(coord.getDir().ordinal()) : null;

				boolean shouldOutput = true;
				while(shouldOutput) {
					shouldOutput = false;
					outer:
					for(int i = indices[2]; i <= indices[3]; i++) {

						ItemStack out = slots[i];

						if(out != null) {

							for(int j = 0; j < (access != null ? access.length : inv.getSizeInventory()); j++) {

								int slot = access != null ? access[j] : j;

								if(!inv.isItemValidForSlot(slot, out))
									continue;

								ItemStack target = inv.getStackInSlot(slot);

								if(InventoryUtil.doesStackDataMatch(out, target) && target.stackSize < target.getMaxStackSize() && target.stackSize < inv.getInventoryStackLimit()) {
									int toDec = Math.min(out.stackSize, Math.min(target.getMaxStackSize(), inv.getInventoryStackLimit()) - target.stackSize);
									this.decrStackSize(i, toDec);
									target.stackSize += toDec;
									shouldOutput = true;
									break outer;
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
									this.decrStackSize(i, 1);
									shouldOutput = true;
									break outer;
								}
							}
						}
					}
				}
			}
		}
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

	private ChemRecipe resolveRecipe(int index) {
		ItemStack template = this.slots[getTemplateIndex(index)];
		int meta = template == null ? 0 : template.getItemDamage();
		if(this.cachedTemplateStacks[index] != template || this.cachedTemplateMeta[index] != meta) {
			this.cachedTemplateStacks[index] = template;
			this.cachedTemplateMeta[index] = meta;
			this.cachedRecipes[index] = template != null && template.getItem() == ModItems.chemistry_template ? ChemplantRecipes.indexMapping.get(meta) : null;
		}
		return this.cachedRecipes[index];
	}

	/*public int getFluidFill(FluidType type) {

		int fill = 0;

		for(FluidTank tank : inTanks()) {
			if(tank.getTankType() == type) {
				fill += tank.getFill();
			}
		}

		for(FluidTank tank : outTanks()) {
			if(tank.getTankType() == type) {
				fill += tank.getFill();
			}
		}

		return fill;
	}*/

	/* For input only! */
	public int getMaxFluidFill(FluidType type) {

		int maxFill = 0;

		for(FluidTank tank : inTanks()) {
			if(tank.getTankType() == type) {
				maxFill += tank.getMaxFill();
			}
		}

		return maxFill;
	}

	protected List<FluidTank> inTanks() {

		List<FluidTank> inTanks = new ArrayList();

		for(int i = 0; i < tanks.length; i++) {
			FluidTank tank = tanks[i];
			if(i % 4 < 2) {
				inTanks.add(tank);
			}
		}

		return inTanks;
	}

	/*public void receiveFluid(int amount, FluidType type) {

		if(amount <= 0)
			return;

		List<FluidTank> rec = new ArrayList();

		for(FluidTank tank : inTanks()) {
			if(tank.getTankType() == type) {
				rec.add(tank);
			}
		}

		if(rec.size() == 0)
			return;

		int demand = 0;
		List<Integer> weight = new ArrayList();

		for(FluidTank tank : rec) {
			int fillWeight = tank.getMaxFill() - tank.getFill();
			demand += fillWeight;
			weight.add(fillWeight);
		}

		for(int i = 0; i < rec.size(); i++) {

			if(demand <= 0)
				break;

			FluidTank tank = rec.get(i);
			int fillWeight = weight.get(i);
			int part = (int) (Math.min((long)amount, (long)demand) * (long)fillWeight / (long)demand);

			tank.setFill(tank.getFill() + part);
		}
	}*/

	public int getFluidFillForTransfer(FluidType type, int pressure) {

		int fill = 0;

		for(FluidTank tank : outTanks()) {
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				fill += tank.getFill();
			}
		}

		return fill;
	}

	public void transferFluid(int amount, FluidType type, int pressure) {

		/*
		 * this whole new fluid mumbo jumbo extra abstraction layer might just be a bandaid
		 * on the gushing wound that is the current fluid systemm but i'll be damned if it
		 * didn't at least do what it's supposed to. half a decade and we finally have multi
		 * tank support for tanks with matching fluid types!!
		 */
		if(amount <= 0)
			return;

		List<FluidTank> send = new ArrayList();

		for(FluidTank tank : outTanks()) {
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				send.add(tank);
			}
		}

		if(send.size() == 0)
			return;

		int offer = 0;
		List<Integer> weight = new ArrayList();

		for(FluidTank tank : send) {
			int fillWeight = tank.getFill();
			offer += fillWeight;
			weight.add(fillWeight);
		}

		int tracker = amount;

		for(int i = 0; i < send.size(); i++) {

			FluidTank tank = send.get(i);
			int fillWeight = weight.get(i);
			int part = amount * fillWeight / offer;

			tank.setFill(tank.getFill() - part);
			tracker -= part;
		}

		//making sure to properly deduct even the last mB lost by rounding errors
		for(int i = 0; i < 100 && tracker > 0; i++) {

			FluidTank tank = send.get(i % send.size());

			if(tank.getFill() > 0) {
				int total = Math.min(tank.getFill(), tracker);
				tracker -= total;
				tank.setFill(tank.getFill() - total);
			}
		}
	}

	protected List<FluidTank> outTanks() {

		List<FluidTank> outTanks = new ArrayList();

		for(int i = 0; i < tanks.length; i++) {
			FluidTank tank = tanks[i];
			if(i % 4 > 1) {
				outTanks.add(tank);
			}
		}

		return outTanks;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public long transferFluid(FluidType type, int pressure, long fluid) {
		int amount = (int) fluid;

		if(amount <= 0)
			return 0;

		List<FluidTank> rec = new ArrayList();

		for(FluidTank tank : inTanks()) {
			if(tank.getTankType() == type && tank.getPressure() == pressure) {
				rec.add(tank);
			}
		}

		if(rec.size() == 0)
			return fluid;

		int demand = 0;
		List<Integer> weight = new ArrayList();

		for(FluidTank tank : rec) {
			int fillWeight = tank.getMaxFill() - tank.getFill();
			demand += fillWeight;
			weight.add(fillWeight);
		}

		for(int i = 0; i < rec.size(); i++) {

			if(demand <= 0)
				break;

			FluidTank tank = rec.get(i);
			int fillWeight = weight.get(i);
			int part = (int) (Math.min((long)amount, (long)demand) * (long)fillWeight / (long)demand);

			tank.setFill(tank.getFill() + part);
			fluid -= part;
		}

		return fluid;
	}

	@Override
	public long getDemand(FluidType type, int pressure) {
		return getMaxFluidFill(type) - getFluidFillForTransfer(type, pressure);
	}

	@Override
	public long getTotalFluidForSend(FluidType type, int pressure) {
		return getFluidFillForTransfer(type, pressure);
	}

	@Override
	public void removeFluidForTransfer(FluidType type, int pressure, long amount) {
		this.transferFluid((int) amount, type, pressure);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getIntArray("progress");

		if(progress.length == 0)
			progress = new int[this.getRecipeCount()];

		for(int i = 0; i < tanks.length; i++) {
			tanks[i].readFromNBT(nbt, "t" + i);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setIntArray("progress", progress);

		for(int i = 0; i < tanks.length; i++) {
			tanks[i].writeToNBT(nbt, "t" + i);
		}
	}

	public abstract int getRecipeCount();
	public abstract int getTankCapacity();
	public abstract int getTemplateIndex(int index);

	/**
	 * @param index
	 * @return A size 4 int array containing min input, max input, min output and max output indices in that order.
	 */
	public abstract int[] getSlotIndicesFromIndex(int index);
	public abstract DirPos[] getInputPositions();
	public abstract DirPos[] getOutputPositions();
}
