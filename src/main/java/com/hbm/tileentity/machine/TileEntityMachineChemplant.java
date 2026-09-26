package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.util.List;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.UpgradeManagerNT;
import com.hbm.inventory.container.ContainerMachineChemplant;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineChemplant;
import com.hbm.inventory.recipes.ChemplantRecipes;
import com.hbm.inventory.recipes.ChemplantRecipes.ChemRecipe;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.InventoryUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineChemplant extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IGUIProvider, IUpgradeInfoProvider {
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_SLOT_CHEMPLANT = 0;
	private ChemRecipe cachedRecipe;
	private long observedRecipeGeneration = -1L;
	private ItemStack observedTemplate;
	private int observedTemplateMeta;
	private boolean cachedEligible;
	private boolean needsInputTransfer = true;
	private boolean runtimeStateInitialized;
	private boolean runtimeEnergyMutation;
	private long nextRuntimeTick = -1L;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long observedPower;
	private final int[] observedTankFill = new int[4];
	private final int[] observedTankCapacity = new int[4];
	private final int[] observedTankPressure = new int[4];
	private final com.hbm.inventory.fluid.FluidType[] observedTankType = new com.hbm.inventory.fluid.FluidType[4];
	private final int[] observedItems = new int[21];
	private int cachedPositionMeta = -1;
	private DirPos[] cachedConnectionPositions;


	public long energyQuanta;
	public static final long maxPower = 100000;
	public int progress;
	public int maxProgress = 100;
	public boolean isProgressing;

	private AudioWrapper audio;

	public FluidTank[] tanks;

	//upgraded stats
	long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(100L);
	int speed = 100;

	public TileEntityMachineChemplant() {
		super(21);
		/*
		 * 0 Battery
		 * 1-3 Upgrades
		 * 4 Schematic
		 * 5-8 Output
		 * 9-10 FOut In
		 * 11-12 FOut Out
		 * 13-16 Input
		 * 17-18 FIn In
		 * 19-20 FIn Out
		 */

		tanks = new FluidTank[4];
		for(int i = 0; i < 4; i++) {
			tanks[i] = new FluidTank(Fluids.NONE, 24_000);
			this.trackMachineFluidTank(tanks[i]);
		}
	}

	@Override
	public String getName() {
		return "container.chemplant";
	}

	// last successful load
	int lsl0 = 0;
	int lsl1 = 0;
	int lsu0 = 0;
	int lsu1 = 0;

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {
			this.networkPackNTIfDirty(150);
		} else {

			if(isProgressing && this.worldObj.getTotalWorldTime() % 3 == 0) {

				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getOpposite();
				ForgeDirection rot = dir.getRotation(ForgeDirection.UP);
				double x = xCoord + 0.5 + dir.offsetX * 1.125 + rot.offsetX * 0.125;
				double y = yCoord + 3;
				double z = zCoord + 0.5 + dir.offsetZ * 1.125 + rot.offsetZ * 0.125;
				worldObj.spawnParticle("cloud", x, y, z, 0.0, 0.1, 0.0);
			}

			float volume = this.getVolume(1F);

			if(isProgressing && volume > 0) {

				if(audio == null) {
					audio = this.createAudioLoop();
					audio.updateVolume(volume);
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
					audio.updateVolume(volume);
				}

			} else {

				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
		}
	}

	@Override
	public void serialize(ByteBuf buf) {
		super.serialize(buf);
		buf.writeLong(energyQuanta);
		buf.writeInt(progress);
		buf.writeInt(maxProgress);
		buf.writeBoolean(isProgressing);

		for(int i = 0; i < tanks.length; i++)
			tanks[i].serialize(buf);
	}

	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		progress = buf.readInt();
		maxProgress = buf.readInt();
		isProgressing = buf.readBoolean();

		for(int i = 0; i < tanks.length; i++)
			tanks[i].deserialize(buf);
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.chemplantOperate", xCoord, yCoord, zCoord, 1.0F, 10F, 1.0F);
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void invalidate() {

		super.invalidate();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	private void updateConnections() {

		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[1].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	public DirPos[] getConPos() {

		int meta = this.getBlockMetadata();
		if(this.cachedConnectionPositions != null && this.cachedPositionMeta == meta) return this.cachedConnectionPositions;
		ForgeDirection dir = ForgeDirection.getOrientation(meta - BlockDummyable.offset).getOpposite();
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);

		this.cachedPositionMeta = meta;
		this.cachedConnectionPositions = new DirPos[] {
				new DirPos(xCoord + rot.offsetX * 3,				yCoord,	zCoord + rot.offsetZ * 3,				rot),
				new DirPos(xCoord - rot.offsetX * 2,				yCoord,	zCoord - rot.offsetZ * 2,				rot.getOpposite()),
				new DirPos(xCoord + rot.offsetX * 3 + dir.offsetX,	yCoord,	zCoord + rot.offsetZ * 3 + dir.offsetZ, rot),
				new DirPos(xCoord - rot.offsetX * 2 + dir.offsetX,	yCoord,	zCoord - rot.offsetZ * 2 + dir.offsetZ, rot.getOpposite())
		};
		return this.cachedConnectionPositions;
	}

	private void setupTanks(ChemRecipe recipe) {
		if(recipe.inputFluids[0] != null) tanks[0].withPressure(recipe.inputFluids[0].pressure).setTankType(recipe.inputFluids[0].type);	else tanks[0].setTankType(Fluids.NONE);
		if(recipe.inputFluids[1] != null) tanks[1].withPressure(recipe.inputFluids[1].pressure).setTankType(recipe.inputFluids[1].type);	else tanks[1].setTankType(Fluids.NONE);
		if(recipe.outputFluids[0] != null) tanks[2].withPressure(recipe.outputFluids[0].pressure).setTankType(recipe.outputFluids[0].type);	else tanks[2].setTankType(Fluids.NONE);
		if(recipe.outputFluids[1] != null) tanks[3].withPressure(recipe.outputFluids[1].pressure).setTankType(recipe.outputFluids[1].type);	else tanks[3].setTankType(Fluids.NONE);
	}

	private boolean hasRequiredFluids(ChemRecipe recipe) {
		if(recipe.inputFluids[0] != null && tanks[0].getFill() < recipe.inputFluids[0].fill) return false;
		if(recipe.inputFluids[1] != null && tanks[1].getFill() < recipe.inputFluids[1].fill) return false;
		return true;
	}

	private boolean hasSpaceForFluids(ChemRecipe recipe) {
		if(recipe.outputFluids[0] != null && tanks[2].getFill() + recipe.outputFluids[0].fill > tanks[2].getMaxFill()) return false;
		if(recipe.outputFluids[1] != null && tanks[3].getFill() + recipe.outputFluids[1].fill > tanks[3].getMaxFill()) return false;
		return true;
	}

	private boolean hasRequiredItems(ChemRecipe recipe) {
		return InventoryUtil.doesArrayHaveIngredients(slots, 13, 16, recipe.inputs);
	}

	private boolean hasSpaceForItems(ChemRecipe recipe) {
		return InventoryUtil.doesArrayHaveSpace(slots, 5, 8, recipe.outputs);
	}

	private void consumeFluids(ChemRecipe recipe) {
		if(recipe.inputFluids[0] != null) tanks[0].setFill(tanks[0].getFill() - recipe.inputFluids[0].fill);
		if(recipe.inputFluids[1] != null) tanks[1].setFill(tanks[1].getFill() - recipe.inputFluids[1].fill);
	}

	private void produceFluids(ChemRecipe recipe) {
		if(recipe.outputFluids[0] != null) tanks[2].setFill(tanks[2].getFill() + recipe.outputFluids[0].fill);
		if(recipe.outputFluids[1] != null) tanks[3].setFill(tanks[3].getFill() + recipe.outputFluids[1].fill);
	}

	private void consumeItems(ChemRecipe recipe) {

		for(AStack in : recipe.inputs) {
			if(in != null)
				InventoryUtil.tryConsumeAStack(slots, 13, 16, in);
		}
	}

	private void produceItems(ChemRecipe recipe) {

		for(ItemStack out : recipe.outputs) {
			if(out != null)
				InventoryUtil.tryAddItemToInventory(slots, 5, 8, out.copy());
		}
	}

	//TODO: move this into a util class
	private boolean loadItems() {
		ChemRecipe recipe = this.cachedRecipe;
		boolean changed = false;

		if(recipe != null) {

			ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getOpposite();

			int x = xCoord - dir.offsetX * 2;
			int z = zCoord - dir.offsetZ * 2;

			TileEntity te = worldObj.getTileEntity(x, yCoord, z);

			if(te instanceof IInventory) {

				IInventory inv = (IInventory) te;
				ISidedInventory sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
				int[] access = sided != null ? sided.getAccessibleSlotsFromSide(dir.ordinal()) : null;

				for(AStack ingredient : recipe.inputs) {

					outer:
					while(!InventoryUtil.doesArrayHaveIngredients(slots, 13, 16, ingredient)) {

						boolean found = false;

						for(int i = 0; i < (access != null ? access.length : inv.getSizeInventory()); i++) {

							int slot = access != null ? access[i] : i;
							ItemStack stack = inv.getStackInSlot(slot);

							if(ingredient.matchesRecipe(stack, true) && (sided == null || sided.canExtractItem(slot, stack, 0))) {

								for(int j = 13; j <= 16; j++) {

									if(slots[j] != null && slots[j].stackSize < slots[j].getMaxStackSize() & InventoryUtil.doesStackDataMatch(slots[j], stack)) {
										inv.decrStackSize(slot, 1);
										slots[j].stackSize++;
										changed = true;
										continue outer;
									}
								}

								for(int j = 13; j <= 16; j++) {

									if(slots[j] == null) {
										slots[j] = stack.copy();
										slots[j].stackSize = 1;
										inv.decrStackSize(slot, 1);
										changed = true;
										continue outer;
									}
								}
							}
						}

						if(!found) break outer;
					}
				}
				if(changed) inv.markDirty();
			}
		}
		return changed;
	}

	private void unloadItems() {

		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset).getOpposite();
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);

		int x = xCoord + dir.offsetX * 3 + rot.offsetX;
		int z = zCoord + dir.offsetZ * 3 + rot.offsetZ;

		TileEntity te = worldObj.getTileEntity(x, yCoord, z);

		if(te instanceof IInventory) {

			IInventory inv = (IInventory) te;
			ISidedInventory sided = inv instanceof ISidedInventory ? (ISidedInventory) inv : null;
			int[] access = sided != null ? sided.getAccessibleSlotsFromSide(dir.ordinal()) : null;

			boolean shouldOutput = true;

			while(shouldOutput) {
				shouldOutput = false;
				outer:
				for(int i = 5; i <= 8; i++) {

					ItemStack out = slots[i];

					if(out != null) {

						for(int j = 0; j < (access != null ? access.length : inv.getSizeInventory()); j++) {

							int slot = access != null ? access[j] : j;

							if(!inv.isItemValidForSlot(slot, out))
								continue;

							ItemStack target = inv.getStackInSlot(slot);

							if(InventoryUtil.doesStackDataMatch(out, target) && target.stackSize < Math.min(target.getMaxStackSize(), inv.getInventoryStackLimit())) {
								int toDec = Math.min(out.stackSize, Math.min(target.getMaxStackSize(), inv.getInventoryStackLimit()) - target.stackSize);
								this.decrStackSize(i, toDec);
								target.stackSize += toDec;
								inv.markDirty();
								shouldOutput = true;
								break outer;
							}
						}

						for(int j = 0; j < (access != null ? access.length : inv.getSizeInventory()); j++) {

							int slot = access != null ? access[j] : j;

							if(!inv.isItemValidForSlot(slot, out))
								continue;

							if(inv.getStackInSlot(slot) == null && (sided != null ? sided.canInsertItem(slot, out, dir.ordinal()) : inv.isItemValidForSlot(slot, out))) {
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

	@Override
	public long getStoredEnergyQuanta() {
		return this.energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		this.markNetworkDirty();
		if(!this.runtimeEnergyMutation) {
			this.cancelAccountingTransition();
			this.markMachineEnergyDirty();
		}
	}

	@Override
	protected void onInventorySlotChanged(int slot) {
		super.onInventorySlotChanged(slot);
		this.cancelAccountingTransition();
		if(slot == 4 || slot >= 13 && slot <= 16) this.needsInputTransfer = true;
		if(slot >= 1 && slot <= 3) this.upgradeManager.invalidate();
	}

	@Override
	public long transferFluid(com.hbm.inventory.fluid.FluidType type, int pressure, long amount) {
		long remainder = IFluidStandardTransceiver.super.transferFluid(type, pressure, amount);
		if(remainder != amount) this.fluidStorageChanged();
		return remainder;
	}

	@Override
	public void removeFluidForTransfer(com.hbm.inventory.fluid.FluidType type, int pressure, long amount) {
		if(amount <= 0L) return;
		IFluidStandardTransceiver.super.removeFluidForTransfer(type, pressure, amount);
		this.fluidStorageChanged();
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public String getMachineRuntimeType() {
		return "hbm:chemplant";
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.CONFIGURATION)) != 0) {
			if(this.recipeChanged()) {
				this.resolveRecipe();
				this.markNetworkDirty();
				this.markDirty();
			}
			this.refreshEligibility();
			this.observeItems();
		}
		this.refreshUpgrades(false);
		this.runtimeStateInitialized = true;
		long now = worldObj.getTotalWorldTime();
		if((causes & MachineDirtyCause.LIFECYCLE) != 0 && this.nextRuntimeTick == now + 1L && this.progress > 0 && this.cachedEligible && this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts)) {
			this.scheduleMachineTransition(this.nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT);
		} else {
			this.runAccountingTick(now);
		}
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_ACCOUNTING || taskSlot != TASK_SLOT_CHEMPLANT || worldObj == null || worldObj.isRemote) return;
		this.nextRuntimeTick = -1L;
		if(this.runtimeStateInitialized) this.runAccountingTick(worldObj.getTotalWorldTime());
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			int fluidDelay = 40;
			boolean fluidContainerChanged = false;
			if(lsu0 >= fluidDelay && tanks[0].loadTank(17, 19, slots)) { lsl0 = 0; fluidContainerChanged = true; }
			if(lsu1 >= fluidDelay && tanks[1].loadTank(18, 20, slots)) { lsl1 = 0; fluidContainerChanged = true; }
			if(lsl0 >= fluidDelay && slots[17] != null && !FluidTank.noDualUnload.contains(slots[17].getItem()) && tanks[0].unloadTank(17, 19, slots)) { lsu0 = 0; fluidContainerChanged = true; }
			if(lsl1 >= fluidDelay && slots[18] != null && !FluidTank.noDualUnload.contains(slots[18].getItem()) && tanks[1].unloadTank(18, 20, slots)) { lsu1 = 0; fluidContainerChanged = true; }
			if(tanks[2].unloadTank(9, 11, slots)) fluidContainerChanged = true;
			if(tanks[3].unloadTank(10, 12, slots)) fluidContainerChanged = true;
			if(lsl0 < fluidDelay) lsl0 = Math.min(fluidDelay, lsl0 + cadence);
			if(lsl1 < fluidDelay) lsl1 = Math.min(fluidDelay, lsl1 + cadence);
			if(lsu0 < fluidDelay) lsu0 = Math.min(fluidDelay, lsu0 + cadence);
			if(lsu1 < fluidDelay) lsu1 = Math.min(fluidDelay, lsu1 + cadence);

			boolean inventoryChanged = this.itemsChanged();
			if(!runtimeStateInitialized || needsInputTransfer) inventoryChanged |= this.loadItems();
			if(this.hasItemsToUnload()) this.unloadItems();
			inventoryChanged |= this.itemsChanged();
			this.observeItems();
			if(inventoryChanged) {
				this.cancelAccountingTransition();
				this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE);
				this.markNetworkDirty();
				this.markDirty();
			}
			if(fluidContainerChanged) this.fluidStorageChanged();
			for(DirPos pos : getConPos()) {
				if(tanks[2].getFill() > 0) this.sendFluid(tanks[2], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
				if(tanks[3].getFill() > 0) this.sendFluid(tanks[3], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			return;
		}
		if(cadence != 20) return;
		boolean recipeChanged = this.recipeChanged();
		boolean tanksChanged = this.tanksChanged();
		boolean itemsChanged = this.itemsChanged();
		boolean upgradesChanged = this.refreshUpgrades(true);
		if(tanksChanged) {
			this.markDirty();
			this.markNetworkDirty();
		}
		if(recipeChanged || tanksChanged || itemsChanged || upgradesChanged || this.energyQuanta != this.observedPower || this.hasBatteryWork() && this.nextRuntimeTick < 0L) {
			this.cancelAccountingTransition();
			this.markMachineDirty((recipeChanged ? MachineDirtyCause.RECIPE : 0) | (tanksChanged ? MachineDirtyCause.FLUID : 0) | (itemsChanged ? MachineDirtyCause.INVENTORY : 0) | (upgradesChanged ? MachineDirtyCause.CONFIGURATION : 0) | MachineDirtyCause.ENERGY);
		}
		this.updateConnections();
	}

	private boolean hasItemsToUnload() {
		for(int i = 5; i <= 8; i++) if(slots[i] != null) return true;
		return false;
	}

	private void runAccountingTick(long now) {
		if(this.lastAccountingTick == now) {
			this.scheduleNextTick(now);
			return;
		}
		this.lastAccountingTick = now;
		boolean recipeChanged = this.recipeChanged();
		if(recipeChanged) {
			this.resolveRecipe();
			this.markNetworkDirty();
			this.markDirty();
		}
		if(recipeChanged || this.tanksChanged()) this.refreshEligibility();
		long oldPower = this.energyQuanta;
		int oldProgress = this.progress;
		int oldMaxProgress = this.maxProgress;
		boolean oldProgressing = this.isProgressing;
		boolean completed = false;

		this.isProgressing = false;
		this.setPowerInternal(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
		if(this.cachedEligible && this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts) && this.cachedRecipe != null) {
			int duration = this.cachedRecipe.getDuration() * this.speed / 100;
			if(duration <= 0) duration = 1;
			if(this.progress + 1 >= duration) this.refreshEligibility();
			if(this.cachedEligible && (this.cachedRecipe.oxygenConsumption <= 0 || this.breatheAir(this.cachedRecipe.oxygenConsumption))) {
				this.isProgressing = true;
				this.setPowerInternal(this.energyQuanta - EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts));
				this.progress++;
				this.maxProgress = duration;
				if(this.progress >= this.maxProgress) {
					this.consumeFluids(this.cachedRecipe);
					this.produceFluids(this.cachedRecipe);
					this.consumeItems(this.cachedRecipe);
					this.produceItems(this.cachedRecipe);
					this.progress = 0;
					completed = true;
					this.refreshEligibility();
					this.observeItems();
				}
			} else this.progress = 0;
		} else this.progress = 0;

		this.observedPower = this.energyQuanta;
		if(oldPower != this.energyQuanta || oldProgress != this.progress || oldMaxProgress != this.maxProgress || oldProgressing != this.isProgressing || completed) {
			this.markDirty();
			this.markNetworkDirty();
		}
		this.scheduleNextTick(now);
	}

	private void scheduleNextTick(long now) {
		if(this.cachedEligible && this.energyQuanta >= EnergyUnits.wattsToQuantaPerTick(this.operatingPowerWatts) || this.hasBatteryWork() || this.progress > 0 || this.isProgressing) {
			if(this.nextRuntimeTick == now + 1L) return;
			this.nextRuntimeTick = now + 1L;
			this.scheduleMachineTransition(this.nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT);
		} else this.cancelAccountingTransition();
	}

	private boolean recipeChanged() {
		ItemStack template = slots[4];
		return this.observedRecipeGeneration != ChemplantRecipes.recipeGeneration || this.observedTemplate != template || this.observedTemplateMeta != (template == null ? 0 : template.getItemDamage());
	}

	private void resolveRecipe() {
		ItemStack template = slots[4];
		this.observedTemplate = template;
		this.observedTemplateMeta = template == null ? 0 : template.getItemDamage();
		this.observedRecipeGeneration = ChemplantRecipes.recipeGeneration;
		this.cachedRecipe = template != null && template.getItem() == ModItems.chemistry_template ? ChemplantRecipes.indexMapping.get(this.observedTemplateMeta) : null;
	}

	private void refreshEligibility() {
		if(this.cachedRecipe != null) this.setupTanks(this.cachedRecipe);
		this.needsInputTransfer = this.cachedRecipe != null && !this.hasRequiredItems(this.cachedRecipe);
		this.cachedEligible = this.cachedRecipe != null && this.hasRequiredFluids(this.cachedRecipe) && this.hasSpaceForFluids(this.cachedRecipe) && !this.needsInputTransfer && this.hasSpaceForItems(this.cachedRecipe);
		this.observeTanks();
	}

	private boolean refreshUpgrades(boolean contentAware) {
		int oldSpeed = this.speed;
		long oldOperatingPowerWatts = this.operatingPowerWatts;
		if(contentAware) this.upgradeManager.checkSlots(slots, 1, 3);
		else this.upgradeManager.checkSlotsIfDirty(slots, 1, 3);
		int speedLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.SPEED), 3);
		int powerLevel = Math.min(this.upgradeManager.getLevel(UpgradeType.POWER), 3);
		int overLevel = this.upgradeManager.getLevel(UpgradeType.OVERDRIVE);
		this.speed = (100 - speedLevel * 25 + powerLevel * 5) / (overLevel + 1);
		if(this.speed <= 0) this.speed = 1;
		long consumptionQuantaPerTick = (100L + speedLevel * 300L - powerLevel * 20L) * (overLevel + 1L);
		this.operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(consumptionQuantaPerTick);
		return oldSpeed != this.speed || oldOperatingPowerWatts != this.operatingPowerWatts;
	}

	private boolean hasBatteryWork() {
		if(this.energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private void setPowerInternal(long value) {
		this.runtimeEnergyMutation = true;
		try { this.setStoredEnergyQuanta(value); } finally { this.runtimeEnergyMutation = false; }
	}

	private void cancelAccountingTransition() {
		this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT);
		this.nextRuntimeTick = -1L;
	}

	private void fluidStorageChanged() {
		if(this.worldObj == null || this.worldObj.isRemote) return;
		this.cancelAccountingTransition();
		this.onFluidStorageChanged();
		this.markNetworkDirty();
		this.markDirty();
	}

	private boolean tanksChanged() {
		for(int i = 0; i < tanks.length; i++) {
			FluidTank tank = tanks[i];
			if(this.observedTankType[i] != tank.getTankType() || this.observedTankPressure[i] != tank.getPressure() || this.observedTankFill[i] != tank.getFill() || this.observedTankCapacity[i] != tank.getMaxFill()) return true;
		}
		return false;
	}

	private void observeTanks() {
		for(int i = 0; i < tanks.length; i++) {
			FluidTank tank = tanks[i];
			this.observedTankType[i] = tank.getTankType();
			this.observedTankPressure[i] = tank.getPressure();
			this.observedTankFill[i] = tank.getFill();
			this.observedTankCapacity[i] = tank.getMaxFill();
		}
	}

	private int itemFingerprint(int index) {
		ItemStack stack = slots[index];
		if(stack == null) return 0;
		Item item = stack.getItem();
		int hash = System.identityHashCode(item);
		hash = 31 * hash + stack.getItemDamage();
		hash = 31 * hash + stack.stackSize;
		return 31 * hash + (stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode());
	}

	private boolean itemsChanged() {
		for(int i = 0; i < observedItems.length; i++) if(observedItems[i] != itemFingerprint(i)) return true;
		return false;
	}

	private void observeItems() {
		for(int i = 0; i < observedItems.length; i++) observedItems[i] = itemFingerprint(i);
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getInteger("progress");
		this.nextRuntimeTick = nbt.hasKey("runtimeChemplantNextTick") ? nbt.getLong("runtimeChemplantNextTick") : -1L;
		this.isProgressing = nbt.hasKey("runtimeChemplantActive") ? nbt.getBoolean("runtimeChemplantActive") : this.progress > 0;
		this.runtimeStateInitialized = false;
		this.needsInputTransfer = true;
		this.observedRecipeGeneration = -1L;

		for(int i = 0; i < tanks.length; i++) {
			tanks[i].readFromNBT(nbt, "t" + i);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("progress", progress);
		nbt.setLong("runtimeChemplantNextTick", this.nextRuntimeTick);
		nbt.setBoolean("runtimeChemplantActive", this.isProgressing);

		for(int i = 0; i < tanks.length; i++) {
			tanks[i].writeToNBT(nbt, "t" + i);
		}
	}

	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 2,
					yCoord,
					zCoord - 2,
					xCoord + 3,
					yCoord + 4,
					zCoord + 3
					);
		}

		return bb;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[2], tanks[3]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0], tanks[1]};
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineChemplant(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineChemplant(player.inventory, this);
	}

	@Override
	public boolean canProvideInfo(UpgradeType type, int level, boolean extendedInfo) {
		return type == UpgradeType.SPEED || type == UpgradeType.POWER || type == UpgradeType.OVERDRIVE;
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_chemplant));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_CONSUMPTION, "+" + (level * 300) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_CONSUMPTION, "-" + (level * 30) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_DELAY, "+" + (level * 5) + "%"));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}

	@Override
	public int getMaxLevel(UpgradeType type) {
		if(type == UpgradeType.SPEED) return 3;
		if(type == UpgradeType.POWER) return 3;
		if(type == UpgradeType.OVERDRIVE) return 9;
		return 0;
	}
}
