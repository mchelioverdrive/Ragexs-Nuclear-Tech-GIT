package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.handler.MultiblockHandlerXR;
import com.hbm.handler.pollution.PollutionHandler;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.OreDictManager;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.container.ContainerMachineRefinery;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineRefinery;
import com.hbm.inventory.recipes.RefineryRecipes;
import com.hbm.items.ModItems;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.*;
import com.hbm.util.ParticleUtil;
import com.hbm.util.Tuple.Quintet;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.fluid.IFluidStandardTransceiver;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineRefinery extends TileEntityMachineBase implements IEnergyReceiverMK2, IOverpressurable, IPersistentNBT, IRepairable, IFluidStandardTransceiver, IGUIProvider, IFluidCopiable {

	public long energyQuanta = 0;
	public int sulfur = 0;
	public static final int maxSulfur = 100;
	public static final long maxPower = 1000;
	public FluidTank[] tanks;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_FIRE = 2;
	private static final int TASK_BATTERY = 3;
	private static final int TASK_POLLUTION = 4;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeAccountingMutation;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long lastRefiningTick = Long.MIN_VALUE;
	private long lastPollutionTick = Long.MIN_VALUE;
	private long batteryTransitionDueTick = Long.MIN_VALUE;
	private long operatingPowerWatts = EnergyUnits.quantaPerTickToWatts(5L);
	private long observedPower;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType cachedFeedType;
	private Quintet<FluidStack, FluidStack, FluidStack, FluidStack, ItemStack> cachedRecipe;
	private FluidStack[] cachedFractions;

	public boolean hasExploded = false;
	public boolean onFire = false;
	public Explosion lastExplosion = null;

	private AudioWrapper audio;
	private int audioTime;
	public boolean isOn;

	private static final int[] slot_access = new int[] {11};

	public TileEntityMachineRefinery() {
		super(13);
		tanks = new FluidTank[5];
		tanks[0] = new FluidTank(Fluids.HOTOIL, 64_000);
		tanks[1] = new FluidTank(Fluids.HEAVYOIL, 24_000);
		tanks[2] = new FluidTank(Fluids.NAPHTHA, 24_000);
		tanks[3] = new FluidTank(Fluids.LIGHTOIL, 24_000);
		tanks[4] = new FluidTank(Fluids.PETROLEUM, 24_000);
		for(FluidTank tank : this.tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.machineRefinery";
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack stack) {
		return false;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "heavy");
		tanks[2].readFromNBT(nbt, "naphtha");
		tanks[3].readFromNBT(nbt, "light");
		tanks[4].readFromNBT(nbt, "petroleum");
		sulfur = nbt.getInteger("sulfur");
		hasExploded = nbt.getBoolean("exploded");
		onFire = nbt.getBoolean("onFire");
		runtimeInitialized = false;
		cachedFeedType = null;
		lastAccountingTick = Long.MIN_VALUE;
		lastRefiningTick = Long.MIN_VALUE;
		lastPollutionTick = Long.MIN_VALUE;
		batteryTransitionDueTick = Long.MIN_VALUE;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleRefiningThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "heavy");
		tanks[2].writeToNBT(nbt, "naphtha");
		tanks[3].writeToNBT(nbt, "light");
		tanks[4].writeToNBT(nbt, "petroleum");
		nbt.setInteger("sulfur", sulfur);
		nbt.setBoolean("exploded", hasExploded);
		nbt.setBoolean("onFire", onFire);
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slot_access;
	}

	@Override
	public boolean canInsertItem(int i, ItemStack itemStack, int j) {
		return this.isItemValidForSlot(i, itemStack);
	}

	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i == 11;
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {
			if(this.getBlockMetadata() < 12) {
				ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata()).getRotation(ForgeDirection.DOWN);
				worldObj.removeTileEntity(xCoord, yCoord, zCoord);
				worldObj.setBlock(xCoord, yCoord, zCoord, ModBlocks.machine_refinery, dir.ordinal() + 10, 3);
				MultiblockHandlerXR.fillSpace(worldObj, xCoord, yCoord, zCoord, ((BlockDummyable) ModBlocks.machine_refinery).getDimensions(), ModBlocks.machine_refinery, dir);
				NBTTagCompound data = new NBTTagCompound();
				this.writeToNBT(data);
				worldObj.getTileEntity(xCoord, yCoord, zCoord).readFromNBT(data);
				return;
			}

		} else {

			if(this.isOn) audioTime = 20;

			if(audioTime > 0) {

				audioTime--;

				if(audio == null) {
					audio = createAudioLoop();
					audio.startSound();
				} else if(!audio.isPlaying()) {
					audio = rebootAudio(audio);
				}

				audio.updateVolume(getVolume(1F));
				audio.keepAlive();

			} else {

				if(audio != null) {
					audio.stopSound();
					audio = null;
				}
			}
		}
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleRefiningThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		lastRefiningTick = Long.MIN_VALUE;
		lastPollutionTick = Long.MIN_VALUE;
		batteryTransitionDueTick = Long.MIN_VALUE;
		super.onChunkUnload();
		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		boolean wasActive = isOn;
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleRefiningThrough(now - 1L);
		if(hasExploded) {
			isOn = false;
			this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
			this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
			batteryTransitionDueTick = Long.MIN_VALUE;
			this.cancelMachineTransition(TASK_POLLUTION, TASK_SLOT_MAIN);
			if(onFire && hasFireFuel()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_FIRE, TASK_SLOT_MAIN);
			else this.cancelMachineTransition(TASK_FIRE, TASK_SLOT_MAIN);
			runtimeInitialized = true;
			return;
		}
		this.cancelMachineTransition(TASK_FIRE, TASK_SLOT_MAIN);
		this.refreshCachedRecipe();
		this.configureRecipeTanks();
		runtimeInitialized = true;
		observedPower = energyQuanta;
		if(wasActive && !this.hasBatteryWork()) this.settleRefiningThrough(now);
		else if(!wasActive) lastAccountingTick = now;
		this.evaluateAndSchedule(now, wasActive && this.hasBatteryWork());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType == TASK_FIRE && taskSlot == TASK_SLOT_MAIN) {
			if(worldObj == null || worldObj.isRemote || !hasExploded || !onFire || !hasFireFuel()) return;
			this.updateFireSimulation();
			if(!isInvalid() && hasExploded && onFire && hasFireFuel()) this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_FIRE, TASK_SLOT_MAIN);
			return;
		}
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized || hasExploded) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			batteryTransitionDueTick = Long.MIN_VALUE;
			this.settleRefiningThrough(now - 1L);
			long beforePower = energyQuanta;
			this.runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower)); }
			finally { this.runtimeEnergyMutation = false; }
			if(beforePower != energyQuanta) { this.markDirty(); this.markNetworkDirty(); }
			this.settleRefiningThrough(now);
			this.observedPower = energyQuanta;
			this.evaluateAndSchedule(now, false);
			return;
		}
		if(taskType == TASK_ACCOUNTING && taskSlot == TASK_SLOT_MAIN) {
			if(this.observeInventoryFingerprint()) {
				boolean wasActive = isOn;
				this.settleRefiningThrough(now - 1L);
				this.refreshCachedRecipe();
				this.configureRecipeTanks();
				if(wasActive && !this.hasBatteryWork()) this.settleRefiningThrough(now);
			}
			this.settleRefiningThrough(now);
			this.observedPower = energyQuanta;
			this.evaluateAndSchedule(now, false);
			return;
		}
		if(taskType == TASK_POLLUTION && taskSlot == TASK_SLOT_MAIN) {
			if(batteryTransitionDueTick <= now) {
				this.scheduleMachineTransition(now, TASK_POLLUTION, TASK_SLOT_MAIN);
				return;
			}
			this.settleRefiningThrough(now);
			if(lastRefiningTick == now && lastPollutionTick != now) {
				PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND * 5);
				lastPollutionTick = now;
			}
			this.evaluateAndSchedule(now, false);
		}
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			long now = worldObj.getTotalWorldTime();
			this.settleRefiningThrough(now);
			if(!hasExploded) {
				boolean fluidChanged = tanks[0].setType(12, slots);
				fluidChanged |= tanks[0].loadTank(1, 2, slots);
				fluidChanged |= tanks[1].unloadTank(3, 4, slots);
				fluidChanged |= tanks[2].unloadTank(5, 6, slots);
				fluidChanged |= tanks[3].unloadTank(7, 8, slots);
				fluidChanged |= tanks[4].unloadTank(9, 10, slots);
				boolean inventoryChanged = this.observeInventoryFingerprint();
				if(fluidChanged || inventoryChanged) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
				for(DirPos pos : getConPos()) for(int i = 1; i < 5; i++) if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
			return;
		}
		if(cadence != 20) return;
		if(!hasExploded) this.updateConnections();
		if(observedPower != energyQuanta) this.markMachineEnergyDirty();
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		for(int i = 0; i < 5; i++) tanks[i].writeToNBT(data, "" + i);
		data.setBoolean("exploded", hasExploded);
		data.setBoolean("onFire", onFire);
		data.setBoolean("isOn", isOn);
		this.networkPack(data, 150);
	}

	private void refreshCachedRecipe() {
		FluidType feed = tanks[0].getTankType();
		if(cachedFeedType != feed) {
			cachedFeedType = feed;
			cachedRecipe = RefineryRecipes.getRefinery(feed);
			cachedFractions = cachedRecipe == null ? null : new FluidStack[] { cachedRecipe.getV(), cachedRecipe.getW(), cachedRecipe.getX(), cachedRecipe.getY() };
		}
	}

	private void configureRecipeTanks() {
		this.refreshCachedRecipe();
		this.beginMachineFluidMutation();
		try {
			for(int i = 0; i < 4; i++) tanks[i + 1].setTankType(cachedFractions == null ? Fluids.NONE : cachedFractions[i].type);
		} finally { this.endMachineFluidMutation(); }
	}

	private boolean canRefine() {
		if(hasExploded || cachedFractions == null || energyQuanta < EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts) || tanks[0].getFill() < 100) return false;
		for(int i = 0; i < 4; i++) if(tanks[i + 1].getFill() + cachedFractions[i].fill > tanks[i + 1].getMaxFill()) return false;
		return true;
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == ModItems.battery_creative || slots[0].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof api.hbm.energymk2.IBatteryItem)) return false;
		api.hbm.energymk2.IBatteryItem battery = (api.hbm.energymk2.IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private void evaluateAndSchedule(long now, boolean batteryDueNow) {
		if(!runtimeInitialized || hasExploded) return;
		boolean canRun = this.canRefine();
		isOn = canRun;
		boolean battery = this.hasBatteryWork();
		if(battery) {
			long due = now + (batteryDueNow ? 0L : 1L);
			this.scheduleMachineTransition(due, TASK_BATTERY, TASK_SLOT_BATTERY);
			if(batteryTransitionDueTick == Long.MIN_VALUE || due < batteryTransitionDueTick) batteryTransitionDueTick = due;
		} else {
			this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
			batteryTransitionDueTick = Long.MIN_VALUE;
		}
		if(canRun && !battery) {
			long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
			long steps = Math.min(tanks[0].getFill() / 100L, energyQuanta / cost);
			for(int i = 0; i < 4; i++) if(cachedFractions[i].fill > 0) steps = Math.min(steps, (tanks[i + 1].getMaxFill() - tanks[i + 1].getFill()) / cachedFractions[i].fill);
			if(steps > 0L) this.scheduleMachineTransition(now + steps, TASK_ACCOUNTING, TASK_SLOT_MAIN);
		} else this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN);
		boolean pollDueNow = now % 20L == 0L && lastRefiningTick == now && lastPollutionTick != now;
		if(pollDueNow) this.scheduleMachineTransition(now, TASK_POLLUTION, TASK_SLOT_MAIN);
		else if(canRun) this.scheduleMachineTransition(now + (20L - now % 20L), TASK_POLLUTION, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_POLLUTION, TASK_SLOT_MAIN);
	}

	private void settleRefiningThrough(long targetTick) {
		if(worldObj == null || worldObj.isRemote || runtimeAccountingMutation || lastAccountingTick == Long.MIN_VALUE || targetTick <= lastAccountingTick) return;
		long startTick = lastAccountingTick;
		long elapsed = targetTick - startTick;
		lastAccountingTick = targetTick;
		if(!runtimeInitialized || hasExploded || !this.canRefine()) return;
		long cost = EnergyUnits.wattsToQuantaPerTick(operatingPowerWatts);
		long steps = Math.min(elapsed, Math.min(tanks[0].getFill() / 100L, energyQuanta / cost));
		for(int i = 0; i < 4; i++) if(cachedFractions[i].fill > 0) steps = Math.min(steps, (tanks[i + 1].getMaxFill() - tanks[i + 1].getFill()) / cachedFractions[i].fill);
		if(steps <= 0L) return;
		long sulfurTotal = (long) sulfur + steps;
		long outputCycles = sulfurTotal / maxSulfur;
		sulfur = (int) (sulfurTotal % maxSulfur);
		runtimeAccountingMutation = true;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			tanks[0].setFill(tanks[0].getFill() - (int) (steps * 100L));
			for(int i = 0; i < 4; i++) tanks[i + 1].setFill(tanks[i + 1].getFill() + (int) (steps * cachedFractions[i].fill));
			this.setStoredEnergyQuanta(energyQuanta - steps * cost);
			this.applySulfurOutputs(outputCycles);
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
			runtimeAccountingMutation = false;
		}
		if(steps > 0L) lastRefiningTick = startTick + steps;
		this.markDirty();
		this.markNetworkDirty();
		if(outputCycles > 0L) this.markMachineDirty(MachineDirtyCause.INVENTORY);
	}

	private void applySulfurOutputs(long cycles) {
		if(cycles <= 0L) return;
		ItemStack out = cachedRecipe == null ? null : cachedRecipe.getZ();
		if(out == null || out.stackSize <= 0) return;
		ItemStack current = slots[11];
		if(current == null) {
			slots[11] = out.copy();
			cycles--;
			current = slots[11];
		}
		if(cycles > 0L && current != null && out.getItem() == current.getItem() && out.getItemDamage() == current.getItemDamage()) {
			long additional = Math.min(cycles, Math.max(0, (current.getMaxStackSize() - current.stackSize) / out.stackSize));
			current.stackSize += (int) (additional * out.stackSize);
		}
	}

	private boolean observeInventoryFingerprint() {
		int hash = 1;
		for(int i = 0; i < slots.length; i++) {
			ItemStack stack = slots[i];
			int tag = stack == null || stack.getItem() instanceof api.hbm.energymk2.IBatteryItem || stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode();
			int slot = stack == null ? 0 : 31 * (31 * (31 * System.identityHashCode(stack.getItem()) + stack.getItemDamage()) + stack.stackSize) + tag;
			hash = 31 * hash + slot;
		}
		boolean changed = inventoryFingerprintInitialized && hash != observedInventoryFingerprint;
		observedInventoryFingerprint = hash;
		inventoryFingerprintInitialized = true;
		return changed;
	}

	private boolean hasFireFuel() {
		for(FluidTank tank : tanks) if(tank.getFill() > 0) return true;
		return false;
	}

	private void updateFireSimulation() {
		boolean hasFuel = false;
		this.beginMachineFluidMutation();
		try {
			for(int i = 0; i < 5; i++) if(tanks[i].getFill() > 0) {
				tanks[i].setFill(Math.max(tanks[i].getFill() - 10, 0));
				hasFuel = true;
			}
		} finally { this.endMachineFluidMutation(); }
		if(!hasFuel) return;
		this.markDirty();
		List<Entity> affected = worldObj.getEntitiesWithinAABB(Entity.class, AxisAlignedBB.getBoundingBox(xCoord - 1.5, yCoord, zCoord - 1.5, xCoord + 2.5, yCoord + 8, zCoord + 2.5));
		for(Entity entity : affected) entity.setFire(5);
		Random rand = worldObj.rand;
		ParticleUtil.spawnGasFlame(worldObj, xCoord + rand.nextDouble(), yCoord + 1.5 + rand.nextDouble() * 3, zCoord + rand.nextDouble(), rand.nextGaussian() * 0.05, 0.1, rand.nextGaussian() * 0.05);
		if(worldObj.getTotalWorldTime() % 20 == 0) PollutionHandler.incrementPollution(worldObj, xCoord, yCoord, zCoord, PollutionType.SOOT, PollutionHandler.SOOT_PER_SECOND * 3);
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.boiler", xCoord, yCoord, zCoord, 0.25F, 15F, 1.0F, 20);
	}

	@Override
	public void invalidate() {

		super.invalidate();

		if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		for(int i = 0; i < 5; i++) tanks[i].readFromNBT(nbt, "" + i);
		this.hasExploded = nbt.getBoolean("exploded");
		this.onFire = nbt.getBoolean("onFire");
		this.isOn = nbt.getBoolean("isOn");
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.trySubscribe(tanks[0].getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	public DirPos[] getConPos() {
		return new DirPos[] {
				new DirPos(xCoord + 2, yCoord, zCoord + 1, Library.POS_X),
				new DirPos(xCoord + 2, yCoord, zCoord - 1, Library.POS_X),
				new DirPos(xCoord - 2, yCoord, zCoord + 1, Library.NEG_X),
				new DirPos(xCoord - 2, yCoord, zCoord - 1, Library.NEG_X),
				new DirPos(xCoord + 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord - 1, yCoord, zCoord + 2, Library.POS_Z),
				new DirPos(xCoord + 1, yCoord, zCoord - 2, Library.NEG_Z),
				new DirPos(xCoord - 1, yCoord, zCoord - 2, Library.NEG_Z)
		};
	}

	public long getPowerScaled(long i) {
		return (energyQuanta * i) / maxPower;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		if(!runtimeEnergyMutation && !runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleRefiningThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleRefiningThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank tank) {
		if(!runtimeAccountingMutation && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleRefiningThrough(worldObj.getTotalWorldTime() - 1L);
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
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] { tanks[1], tanks[2], tanks[3], tanks[4] };
	}

	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tanks[0] };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public void explode(World world, int x, int y, int z) {

		if(this.hasExploded) return;

		this.hasExploded = true;
		this.onFire = true;
		this.markChanged();
		this.markMachineDirty(MachineDirtyCause.ENVIRONMENT | MachineDirtyCause.LIFECYCLE);
	}

	@Override
	public void tryExtinguish(World world, int x, int y, int z, EnumExtinguishType type) {
		if(!this.hasExploded || !this.onFire) return;

		if(type == EnumExtinguishType.FOAM || type == EnumExtinguishType.CO2) {
			this.onFire = false;
			this.markChanged();
			this.markMachineDirty(MachineDirtyCause.ENVIRONMENT);
			return;
		}

		if(type == EnumExtinguishType.WATER) {
			for(FluidTank tank : tanks) {
				if(tank.getFill() > 0) {
					worldObj.newExplosion(null, xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, 5F, true, true);
					return;
				}
			}
		}
	}

	@Override
	public boolean isDamaged() {
		return this.hasExploded;
	}

	List<AStack> repair = new ArrayList();
	@Override
	public List<AStack> getRepairMaterials() {

		if(!repair.isEmpty())
			return repair;

		repair.add(new OreDictStack(OreDictManager.STEEL.plate(), 8));
		repair.add(new ComparableStack(ModItems.ducttape, 4));
		return repair;
	}

	@Override
	public void repair() {
		this.hasExploded = false;
		this.markChanged();
		this.markMachineDirty(MachineDirtyCause.LIFECYCLE | MachineDirtyCause.FLUID | MachineDirtyCause.ENERGY);
	}

	@Override
	public void writeNBT(NBTTagCompound nbt) {
		if(tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && tanks[2].getFill() == 0 && tanks[3].getFill() == 0 && tanks[4].getFill() == 0 && !this.hasExploded) return;
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < 5; i++) this.tanks[i].writeToNBT(data, "" + i);
		data.setBoolean("hasExploded", hasExploded);
		data.setBoolean("onFire", onFire);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < 5; i++) this.tanks[i].readFromNBT(data, "" + i);
		this.hasExploded = data.getBoolean("hasExploded");
		this.onFire = data.getBoolean("onFire");
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineRefinery(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineRefinery(player.inventory, this);
	}

}
