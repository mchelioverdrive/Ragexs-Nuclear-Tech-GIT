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

public class TileEntityMachineChemplant extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IGUIProvider, IUpgradeInfoProvider, com.hbm.realsim.IMachineInfo, com.hbm.realsim.ProcessMachine.Cooling {
    private final com.hbm.realsim.OutputBudget coolingBudget = new com.hbm.realsim.OutputBudget();
    @Override public long availableCoolingQuanta(long tick) { return coolingBudget.available(tick, getMachineCoolingWatts() / 10); }
    @Override public void rejectHeatQuanta(long tick, long quanta) { coolingBudget.use(tick, getMachineCoolingWatts() / 10, quanta); }
    @Override public long getMachineCoolingWatts() { return com.hbm.realsim.IndustrialDemand.CHEMICAL_COOLING_W; }
    @Override public long getProcessAuxiliaryWatts(com.hbm.realsim.ProcessRecipe recipe) { return Math.max(500, com.hbm.realsim.PetroleumProcesses.ceilDiv(recipe.rejectedWatts, 500) * 10); }
	private final UpgradeManagerNT upgradeManager = new UpgradeManagerNT();
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_SLOT_CHEMPLANT = 0;
	private static final int TASK_SLOT_BATTERY = 1;
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
	private long lastChargeTick = Long.MIN_VALUE;
	private long oxygenBlockedUntil;
	private long clientProgressTick;
	private boolean runtimeActive;
	private boolean runtimeSettling;
	private long observedPower;
	private final int[] observedTankFill = new int[8];
	private final int[] observedTankCapacity = new int[8];
	private final int[] observedTankPressure = new int[8];
	private final com.hbm.inventory.fluid.FluidType[] observedTankType = new com.hbm.inventory.fluid.FluidType[8];
	private final int[] observedItems = new int[29];
	private int cachedPositionMeta = -1;
	private DirPos[] cachedConnectionPositions;


	public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
    public long energyQuanta;
	public static final long maxPower = com.hbm.realsim.IndustrialDemand.CHEMICAL_BUFFER_QUANTA;
	public int progress;
	public int maxProgress = 100;
	public boolean isProgressing;

	private AudioWrapper audio;

	public FluidTank[] tanks;

	//upgraded stats
	long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(100L);
	int speed = 100;

	public TileEntityMachineChemplant() {
		super(29);
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

		tanks = new FluidTank[8];
		for(int i = 0; i < tanks.length; i++) {
			tanks[i] = new FluidTank(Fluids.NONE, 24_000);
			this.trackMachineFluidTank(tanks[i]);
		}
	}

	@Override
	public String getName() {
		return "container.chemplant";
	}

    public static int inputContainerSlot(int channel) { return channel < 2 ? 17 + channel : 21 + channel - 2; }
    public static int inputReturnSlot(int channel) { return channel < 2 ? 19 + channel : 23 + channel - 2; }
    public static int outputContainerSlot(int channel) { return channel < 2 ? 9 + channel : 25 + channel - 2; }
    public static int outputReturnSlot(int channel) { return channel < 2 ? 11 + channel : 27 + channel - 2; }
    private final int[] lastLoad = new int[4], lastUnload = new int[4];

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {

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
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
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
            for(int i = 4; i < 6; i++) if(tanks[i].getTankType() != Fluids.NONE) this.trySubscribe(tanks[i].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
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

    public FluidTank[] processInputs() { return new FluidTank[] {tanks[0], tanks[1], tanks[4], tanks[5]}; }
    public FluidTank[] processOutputs() { return new FluidTank[] {tanks[2], tanks[3], tanks[6], tanks[7]}; }

	    private FluidTank inputTank(int i) { return tanks[i < 2 ? i : i + 2]; }
    private FluidTank outputTank(int i) { return tanks[i < 2 ? i + 2 : i + 4]; }

    private void setupTanks(ChemRecipe recipe) {
        for(int i = 0; i < 4; i++) {
            inputTank(i).configureForRecipe(recipe.inputFluids[i] == null ? Fluids.NONE : recipe.inputFluids[i].type, recipe.inputFluids[i] == null ? 0 : recipe.inputFluids[i].pressure);
            outputTank(i).configureForRecipe(recipe.outputFluids[i] == null ? Fluids.NONE : recipe.outputFluids[i].type, recipe.outputFluids[i] == null ? 0 : recipe.outputFluids[i].pressure);
        }
    }

	private boolean hasRequiredFluids(ChemRecipe recipe) {
        for(int i = 0; i < 4; i++) if(recipe.inputFluids[i] != null) {
            FluidTank tank = inputTank(i); com.hbm.inventory.FluidStack need = recipe.inputFluids[i];
            if(tank.getTankType() != need.type || tank.getPressure() != need.pressure || tank.getFill() < need.fill) return false;
        }
        return true;
    }

	private boolean hasSpaceForFluids(ChemRecipe recipe) {
        for(int i = 0; i < 4; i++) if(recipe.outputFluids[i] != null) {
            FluidTank tank = outputTank(i); com.hbm.inventory.FluidStack need = recipe.outputFluids[i];
            if(tank.getTankType() != need.type || tank.getPressure() != need.pressure || (long) tank.getFill() + need.fill > tank.getMaxFill()) return false;
        }
        return true;
    }

	private boolean hasRequiredItems(ChemRecipe recipe) {
		return InventoryUtil.doesArrayHaveIngredients(slots, 13, 16, recipe.inputs);
	}

	private boolean hasSpaceForItems(ChemRecipe recipe) {
		return InventoryUtil.doesArrayHaveSpace(slots, 5, 8, recipe.outputs);
	}

	private void consumeFluids(ChemRecipe recipe) {
        for(int i = 0; i < 4; i++) if(recipe.inputFluids[i] != null) { FluidTank tank = inputTank(i); tank.setFill(tank.getFill() - recipe.inputFluids[i].fill); }
    }

	private void produceFluids(ChemRecipe recipe) {
        for(int i = 0; i < 4; i++) if(recipe.outputFluids[i] != null) { FluidTank tank = outputTank(i); tank.setFill(tank.getFill() + recipe.outputFluids[i].fill); }
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
                    if(ingredient == null) continue;

					outer:
					while(!InventoryUtil.doesArrayHaveIngredients(slots, 13, 16, ingredient)) {

						boolean found = false;

						for(int i = 0; i < (access != null ? access.length : inv.getSizeInventory()); i++) {

							int slot = access != null ? access[i] : i;
							ItemStack stack = inv.getStackInSlot(slot);

							if(ingredient.matchesRecipe(stack, true) && (sided == null || sided.canExtractItem(slot, stack, dir.ordinal()))) {

								for(int j = 13; j <= 16; j++) {

									if(slots[j] != null && slots[j].stackSize < slots[j].getMaxStackSize() && InventoryUtil.doesStackDataMatch(slots[j], stack)) {
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

							if((sided == null || sided.canInsertItem(slot, out, dir.ordinal())) && InventoryUtil.doesStackDataMatch(out, target) && target.stackSize < Math.min(target.getMaxStackSize(), inv.getInventoryStackLimit())) {
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
	public void prepareEnergyTransfer() {
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		if(!runtimeEnergyMutation && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank tank) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
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
		long now = worldObj.getTotalWorldTime();
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleProgressThrough(now - 1L);
		boolean wasActive = runtimeActive;
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
		runtimeActive = wasActive && cachedEligible && energyQuanta >= (cachedRecipe == null ? EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) : cachedRecipe.costPerTick(EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts))) && now >= oxygenBlockedUntil;
		this.settleProgressThrough(now);
		runtimeActive = cachedEligible && energyQuanta >= (cachedRecipe == null ? EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) : cachedRecipe.costPerTick(EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts))) && now >= oxygenBlockedUntil;
		if(runtimeActive && cachedRecipe != null) this.maxProgress = cachedRecipe.durationAtSpeed(speed);
		this.scheduleNextBoundary(now);
		this.networkPackNTIfDirty(150);
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeStateInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleProgressThrough(now);
			this.chargeRuntimeBattery(now);
			if(cachedEligible && energyQuanta >= (cachedRecipe == null ? EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) : cachedRecipe.costPerTick(EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts))) && now >= oxygenBlockedUntil) runtimeActive = true;
			this.scheduleNextBoundary(now);
			return;
		} else if(taskType == TASK_ACCOUNTING && taskSlot == TASK_SLOT_CHEMPLANT) {
			if(oxygenBlockedUntil > 0L && now >= oxygenBlockedUntil) {
				this.settleProgressThrough(now - 1L);
				oxygenBlockedUntil = 0L;
				runtimeActive = cachedEligible && energyQuanta >= (cachedRecipe == null ? EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) : cachedRecipe.costPerTick(EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts)));
			}
			this.settleProgressThrough(now);
		} else return;
		this.scheduleNextBoundary(now);
		this.networkPackNTIfDirty(150);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		this.settleProgressThrough(worldObj.getTotalWorldTime());
		if(cadence == 5) {
			int fluidDelay = 40;
			boolean fluidContainerChanged = false;
            for(int channel = 0; channel < 4; channel++) {
                int in = inputContainerSlot(channel), out = inputReturnSlot(channel);
                if(lastUnload[channel] >= fluidDelay && inputTank(channel).loadTank(in, out, slots)) { lastLoad[channel] = 0; fluidContainerChanged = true; }
                if(lastLoad[channel] >= fluidDelay && slots[in] != null && !FluidTank.noDualUnload.contains(slots[in].getItem())
                        && inputTank(channel).unloadTank(in, out, slots)) { lastUnload[channel] = 0; fluidContainerChanged = true; }
                if(outputTank(channel).unloadTank(outputContainerSlot(channel), outputReturnSlot(channel), slots)) fluidContainerChanged = true;
                lastLoad[channel] = Math.min(fluidDelay, lastLoad[channel] + cadence);
                lastUnload[channel] = Math.min(fluidDelay, lastUnload[channel] + cadence);
            }

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
                for(int i = 6; i < 8; i++) if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
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
		this.markNetworkDirty();
		this.networkPackNTIfDirty(150);
	}

	private boolean hasItemsToUnload() {
		for(int i = 5; i <= 8; i++) if(slots[i] != null) return true;
		return false;
	}

	private void settleProgressThrough(long target) {
		if(runtimeSettling || lastAccountingTick == Long.MIN_VALUE || target <= lastAccountingTick) return;
		long cursor = lastAccountingTick;
        if(physicalBatch.active() || cachedRecipe != null && cachedRecipe.physicalBill != null) {
            runtimeSettling = true; runtimeEnergyMutation = true; this.beginMachineFluidMutation();
            try {
                int before = physicalBatch.ticks();
                com.hbm.realsim.ProcessMachine.step(this, physicalBatch, cachedRecipe == null ? null : cachedRecipe.physicalBill,
                    processInputs(), processOutputs(), slots, 13, 16, 5, 8);
                isProgressing = before != physicalBatch.ticks(); runtimeActive = isProgressing;
                progress = physicalBatch.ticks(); maxProgress = physicalBatch.active() ? physicalBatch.recipe().duration : cachedRecipe == null ? 1 : cachedRecipe.getDuration();
                this.observeItems(); this.observeTanks(); this.refreshEligibility();
                this.markNetworkDirty();
            } finally { this.endMachineFluidMutation(); runtimeSettling = false; runtimeEnergyMutation = false; }
            lastAccountingTick = target; observedPower = energyQuanta; return;
        }
		if(!runtimeActive || cachedRecipe == null) {
			lastAccountingTick = target;
			if(progress != 0 || isProgressing) { progress = 0; isProgressing = false; this.markDirty(); this.markNetworkDirty(); }
			return;
		}
		runtimeSettling = true;
		boolean changed = false;
		try {
			while(cursor < target && runtimeActive) {
				if(this.recipeChanged()) this.resolveRecipe();
				this.refreshEligibility();
				if(!cachedEligible) {
					progress = 0;
					isProgressing = false;
					runtimeActive = false;
					changed = true;
					break;
				}
				int duration = cachedRecipe.durationAtSpeed(speed);
				long cost = (cachedRecipe == null ? EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) : cachedRecipe.costPerTick(EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts)));
				long available = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost;
				long whole = cachedRecipe.oxygenConsumption > 0 ? 0L : Math.min(target - cursor, Math.min((long) duration - progress - 1L, available));
				if(whole > 0L) {
					progress += (int) whole;
					if(cost > 0L) this.setPowerInternal(energyQuanta - whole * cost);
					cursor += whole;
					isProgressing = true;
					changed = true;
					if(cursor >= target) break;
				}
				cursor++;
				if(!cachedEligible || energyQuanta < cost) {
					progress = 0;
					isProgressing = false;
					runtimeActive = false;
					changed = true;
					break;
				}
				if(progress + 1 >= duration) this.refreshEligibility();
				if(!cachedEligible || cachedRecipe.oxygenConsumption > 0 && !this.breatheAir(cachedRecipe.oxygenConsumption)) {
					if(cachedEligible) oxygenBlockedUntil = cursor + 20L;
					progress = 0;
					isProgressing = false;
					runtimeActive = false;
					changed = true;
					break;
				}
				this.setPowerInternal(energyQuanta - cost);
				progress++;
				maxProgress = duration;
				isProgressing = true;
				changed = true;
				if(progress >= duration) {
					this.consumeFluids(cachedRecipe);
					this.produceFluids(cachedRecipe);
					this.consumeItems(cachedRecipe);
					this.produceItems(cachedRecipe);
					progress = 0;
					this.refreshEligibility();
					this.observeItems();
					runtimeActive = cachedEligible && energyQuanta >= cost;
				}
			}
		} finally { runtimeSettling = false; }
		lastAccountingTick = target;
		observedPower = energyQuanta;
		if(changed) { this.markDirty(); this.markNetworkDirty(); }
	}

	private boolean chargeRuntimeBattery(long now) {
		if(lastChargeTick == now) return false;
		lastChargeTick = now;
		long before = energyQuanta;
		this.setPowerInternal(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
		if(before != energyQuanta) { this.markDirty(); this.markNetworkDirty(); }
		return before != energyQuanta;
	}

	private void scheduleNextBoundary(long now) {
		this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT);
        if(physicalBatch.active() || cachedRecipe != null && cachedRecipe.physicalBill != null) {
            if(cachedEligible || physicalBatch.active()) { nextRuntimeTick = now + 1L; this.scheduleMachineTransition(nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT); }
            else nextRuntimeTick = -1L;
            if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
            else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
            return;
        }
		long cost = (cachedRecipe == null ? EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) : cachedRecipe.costPerTick(EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts)));
		if(runtimeActive && cachedEligible && cachedRecipe != null && energyQuanta >= cost) {
			int duration = cachedRecipe.durationAtSpeed(speed);
			long poweredTicks = cost <= 0L ? Long.MAX_VALUE : energyQuanta / cost;
			long delay = Math.max(1L, Math.min(cachedRecipe.oxygenConsumption > 0 ? 1L : duration - progress, poweredTicks));
			nextRuntimeTick = now + delay;
			this.scheduleMachineTransition(nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT);
		} else if(oxygenBlockedUntil > now || progress > 0 || isProgressing) {
			nextRuntimeTick = oxygenBlockedUntil > now ? oxygenBlockedUntil : now + 1L;
			this.scheduleMachineTransition(nextRuntimeTick, TASK_ACCOUNTING, TASK_SLOT_CHEMPLANT);
		} else nextRuntimeTick = -1L;
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
	}

	public int getDisplayedProgress() {
		if(worldObj == null || !worldObj.isRemote || !runtimeActive) return progress;
		return (int) Math.min(maxProgress, progress + Math.max(0L, worldObj.getTotalWorldTime() - clientProgressTick));
	}

	private boolean recipeChanged() {
		ItemStack template = slots[4];
		return this.observedRecipeGeneration != ChemplantRecipes.recipeGeneration || this.observedTemplate != template || this.observedTemplateMeta != (template == null ? 0 : template.getItemDamage());
	}

	private void resolveRecipe() {
		ChemRecipe previous = cachedRecipe;
		ItemStack template = slots[4];
		this.observedTemplate = template;
		this.observedTemplateMeta = template == null ? 0 : template.getItemDamage();
		this.observedRecipeGeneration = ChemplantRecipes.recipeGeneration;
		this.cachedRecipe = template != null && template.getItem() == ModItems.chemistry_template ? ChemplantRecipes.indexMapping.get(this.observedTemplateMeta) : null;
		if(previous != null && previous != cachedRecipe && !physicalBatch.active()) progress = 0;
	}

	private void refreshEligibility() {
        if(physicalBatch.active() || cachedRecipe != null && cachedRecipe.physicalBill != null) {
            com.hbm.realsim.ProcessMachine.sizeProcessBuffers(physicalBatch.active() ? physicalBatch.recipe() : cachedRecipe == null ? null : cachedRecipe.physicalBill, processInputs(), processOutputs());
            com.hbm.realsim.ProcessMachine.configure(physicalBatch, cachedRecipe == null ? null : cachedRecipe.physicalBill, processOutputs());
            needsInputTransfer = !physicalBatch.active() && !hasRequiredItems(cachedRecipe);
            cachedEligible = physicalBatch.active() || com.hbm.realsim.ProcessMachine.inputsReady(cachedRecipe.physicalBill, processInputs(), slots, 13, 16);
            this.observeTanks(); return;
        }
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
		this.speed = Math.max(1, this.speed);
		this.operatingPowerWatts = com.hbm.realsim.IndustrialDemand.atSpeed(com.hbm.realsim.IndustrialDemand.CHEMICAL_W, this.speed, powerLevel);
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

    @Override public int[] getAccessibleSlotsFromSide(int side) {
        int[] access = new int[slots.length]; for(int i = 0; i < access.length; i++) access[i] = i; return access;
    }
    @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if(stack == null) return false;
        if(slot == 0) return stack.getItem() instanceof IBatteryItem || stack.getItem() == ModItems.battery_creative;
        if(slot >= 1 && slot <= 3) return stack.getItem() instanceof com.hbm.items.machine.ItemMachineUpgrade;
        if(slot == 4) return stack.getItem() == ModItems.chemistry_template;
        if(slot >= 13 && slot <= 16) return true;
        for(int channel = 0; channel < 4; channel++) {
            if(slot == inputContainerSlot(channel)) return acceptsContainer(inputTank(channel), stack, true);
            if(slot == outputContainerSlot(channel)) return acceptsContainer(outputTank(channel), stack, false);
        }
        return false;
    }
    private boolean acceptsContainer(FluidTank tank, ItemStack stack, boolean input) {
        if(stack.getItem() instanceof api.hbm.fluid.IFillableItem) {
            api.hbm.fluid.IFillableItem item = (api.hbm.fluid.IFillableItem) stack.getItem();
            return input && item.providesFluid(tank.getTankType(), stack) || item.acceptsFluid(tank.getTankType(), stack);
        }
        if(stack.getItem() instanceof com.hbm.items.machine.ItemInfiniteFluid) return true;
        return input && com.hbm.inventory.FluidContainerRegistry.getFluidType(stack) == tank.getTankType()
                && tank.getTankType() != Fluids.NONE
                || com.hbm.inventory.FluidContainerRegistry.getContainer(tank.getTankType(), stack) != null;
    }
    @Override public boolean canExtractItem(int slot, ItemStack stack, int side) {
        if(slot >= 5 && slot <= 8) return true;
        for(int channel = 0; channel < 4; channel++) {
            if(slot == inputReturnSlot(channel) || slot == outputReturnSlot(channel)) return true;
            if(slot == inputContainerSlot(channel) && stack != null && stack.getItem() instanceof api.hbm.fluid.IFillableItem)
                return ((api.hbm.fluid.IFillableItem) stack.getItem()).getFill(stack) == 0;
            if(slot == outputContainerSlot(channel) && stack != null && stack.getItem() instanceof api.hbm.fluid.IFillableItem)
                return ((api.hbm.fluid.IFillableItem) stack.getItem()).getFill(stack) > 0;
        }
        return false;
    }

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
        physicalBatch.read(nbt);
        physicalBatch.electrifyUtilities();
        coolingBudget.read(nbt.getCompoundTag("realSimCooling"));
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.progress = nbt.getInteger("progress");
		if(!nbt.hasKey("realSimProcessVersion") && slots[4] != null && slots[4].getItemDamage() == 58) this.progress = 0;
		this.nextRuntimeTick = nbt.hasKey("runtimeChemplantNextTick") ? nbt.getLong("runtimeChemplantNextTick") : -1L;
		this.isProgressing = nbt.hasKey("runtimeChemplantActive") ? nbt.getBoolean("runtimeChemplantActive") : this.progress > 0;
		this.runtimeActive = this.isProgressing;
		this.lastAccountingTick = Long.MIN_VALUE;
		this.runtimeStateInitialized = false;
		this.needsInputTransfer = true;
		this.observedRecipeGeneration = -1L;

		for(int i = 0; i < tanks.length; i++) {
			tanks[i].readFromNBT(nbt, "t" + i);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
        physicalBatch.write(nbt);
        NBTTagCompound coolingTag = new NBTTagCompound(); coolingBudget.write(coolingTag); nbt.setTag("realSimCooling", coolingTag);
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("progress", progress);
		nbt.setInteger("realSimProcessVersion", 1);
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
		return processOutputs();
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return processInputs();
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
			info.add("Electrical demand scales with throughput; see the machine rating");
		}
		if(type == UpgradeType.POWER) {
			info.add("Work reduction: " + (Math.min(3, level) * 10) + "% (standard recipes)");
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

    @Override public long getMachineDemandWatts() { return cachedRecipe != null && cachedRecipe.physicalBill != null ? cachedRecipe.physicalBill.electricalRatingWatts + getProcessAuxiliaryWatts(cachedRecipe.physicalBill) : cachedRecipe == null ? operatingPowerWatts : cachedRecipe.costPerTick(operatingPowerWatts / 10L) * 10L; }
    @Override public long getMachineCurrentDrawWatts() { return physicalBatch.active() || cachedRecipe != null && cachedRecipe.physicalBill != null ? physicalBatch.currentDrawWatts(worldObj == null ? Long.MIN_VALUE : worldObj.getTotalWorldTime()) : isProgressing ? getMachineDemandWatts() : 0; }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        if(cachedRecipe != null && cachedRecipe.physicalBill != null) com.hbm.realsim.MachineInfo.process(lines, physicalBatch.active() ? physicalBatch.recipe() : cachedRecipe.physicalBill);
        else lines.add("Recipe utility families; idle load 0 W. No universal reaction-energy claim.");
    }
}
