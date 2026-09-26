package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.container.ContainerMachineVacuumDistill;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineVacuumDistill;
import com.hbm.inventory.recipes.RefineryRecipes;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.IFluidCopiable;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.IPersistentNBT;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.Tuple.Quartet;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineVacuumDistill extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT, IGUIProvider, IFluidCopiable {
	
	public long energyQuanta;
	public static final long maxPower = 1_000_000;
	
	public FluidTank[] tanks;
	
	private AudioWrapper audio;
	private int audioTime;
	public boolean isOn;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private FluidType observedInputType;
	private long observedRecipeRevision;
	private Quartet<FluidStack, FluidStack, FluidStack, FluidStack> cachedRecipe;
	private static final int TASK_REFINE = 1;
	private static final int TASK_SLOT_MAIN = 0;

	public TileEntityMachineVacuumDistill() {
		super(12);
		
		this.tanks = new FluidTank[5];
		this.tanks[0] = new FluidTank(Fluids.OIL, 64_000).withPressure(2);
		this.tanks[1] = new FluidTank(Fluids.HEAVYOIL_VACUUM, 24_000);
		this.tanks[2] = new FluidTank(Fluids.REFORMATE, 24_000);
		this.tanks[3] = new FluidTank(Fluids.LIGHTOIL_VACUUM, 24_000);
		this.tanks[4] = new FluidTank(Fluids.SOURGAS, 24_000);
		for(FluidTank tank : tanks) this.trackMachineFluidTank(tank);
	}

	@Override
	public String getName() {
		return "container.vacuumDistill";
	}

	@Override
	public void updateEntity() {
		if(!worldObj.isRemote) return;
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
		} else if(audio != null) {
			audio.stopSound();
			audio = null;
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshRuntimeState();
		FluidType inputType = tanks[0].getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != inputType) this.updateConnections();
		observedInputType = inputType;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_REFINE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long oldEnergy = energyQuanta;
		int oldInventoryFingerprint = this.inventoryFingerprint();
		int oldInputFill = tanks[0].getFill();
		int oldHeavyFill = tanks[1].getFill();
		int oldReformateFill = tanks[2].getFill();
		int oldLightFill = tanks[3].getFill();
		int oldGasFill = tanks[4].getFill();
		isOn = false;
		this.beginMachineFluidMutation();
		runtimeEnergyMutation = true;
		try {
			this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
			this.refineBatch();
			this.unloadOutputContainers();
		} finally {
			runtimeEnergyMutation = false;
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
		boolean inventoryChanged = oldInventoryFingerprint != observedInventoryFingerprint;
		if(inventoryChanged) this.markNetworkDirty();
		if(oldEnergy != energyQuanta || inventoryChanged || oldInputFill != tanks[0].getFill() || oldHeavyFill != tanks[1].getFill() || oldReformateFill != tanks[2].getFill() || oldLightFill != tanks[3].getFill() || oldGasFill != tanks[4].getFill()) this.markDirty();
		this.sendOutputFluids();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
		} else if(cadence == 20) {
			this.updateConnections();
		} else if(cadence == 100) {
			long revision = SerializableRecipe.getRegistryRevision();
			if(revision != observedRecipeRevision) {
				observedRecipeRevision = revision;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}
	
	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.boiler", xCoord, yCoord, zCoord, 0.25F, 15F, 1.0F, 20);
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
	
	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);
		
		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.isOn = nbt.getBoolean("isOn");
		for(int i = 0; i < 5; i++) tanks[i].readFromNBT(nbt, "" + i);
	}
	
	private void refreshRuntimeState() {
		this.beginMachineFluidMutation();
		try {
			tanks[0].setType(11, slots);
			tanks[0].loadTank(1, 2, slots);
			cachedRecipe = RefineryRecipes.getVacuum(tanks[0].getTankType());
			if(cachedRecipe == null) {
				for(int i = 1; i < 5; i++) tanks[i].setTankType(Fluids.NONE);
			} else {
				tanks[1].setTankType(cachedRecipe.getW().type);
				tanks[2].setTankType(cachedRecipe.getX().type);
				tanks[3].setTankType(cachedRecipe.getY().type);
				tanks[4].setTankType(cachedRecipe.getZ().type);
			}
			this.unloadOutputContainers();
		} finally {
			this.endMachineFluidMutation();
		}
		this.observeInventoryFingerprint();
	}

	private boolean canRefine() {
		if(cachedRecipe == null || energyQuanta < 10_000 || tanks[0].getFill() < 100) return false;
		return tanks[1].getFill() + cachedRecipe.getW().fill <= tanks[1].getMaxFill()
				&& tanks[2].getFill() + cachedRecipe.getX().fill <= tanks[2].getMaxFill()
				&& tanks[3].getFill() + cachedRecipe.getY().fill <= tanks[3].getMaxFill()
				&& tanks[4].getFill() + cachedRecipe.getZ().fill <= tanks[4].getMaxFill();
	}

	private void refineBatch() {
		isOn = false;
		if(!this.canRefine()) return;
		isOn = true;
		this.setStoredEnergyQuanta(energyQuanta - 10_000);
		tanks[0].setFill(tanks[0].getFill() - 100);
		tanks[1].setFill(tanks[1].getFill() + cachedRecipe.getW().fill);
		tanks[2].setFill(tanks[2].getFill() + cachedRecipe.getX().fill);
		tanks[3].setFill(tanks[3].getFill() + cachedRecipe.getY().fill);
		tanks[4].setFill(tanks[4].getFill() + cachedRecipe.getZ().fill);
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[0] == null) return false;
		if(slots[0].getItem() == com.hbm.items.ModItems.battery_creative || slots[0].getItem() == com.hbm.items.ModItems.fusion_core_infinite) return true;
		if(!(slots[0].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[0].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[0]) > 0;
	}

	private boolean hasFluidOutput() {
		return tanks[1].getFill() > 0 || tanks[2].getFill() > 0 || tanks[3].getFill() > 0 || tanks[4].getFill() > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(this.canRefine() || this.hasBatteryWork() || this.hasFluidOutput()) this.scheduleMachineTransition(now + 1L, TASK_REFINE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_REFINE, TASK_SLOT_MAIN);
	}

	private void unloadOutputContainers() {
		tanks[1].unloadTank(3, 4, slots);
		tanks[2].unloadTank(5, 6, slots);
		tanks[3].unloadTank(7, 8, slots);
		tanks[4].unloadTank(9, 10, slots);
	}

	private void sendOutputFluids() {
		for(DirPos pos : getConPos()) {
			for(int i = 1; i < 5; i++) {
				if(tanks[i].getFill() > 0) this.sendFluid(tanks[i], worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			}
		}
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, energyQuanta);
		data.setBoolean("isOn", isOn);
		for(int i = 0; i < 5; i++) tanks[i].writeToNBT(data, "" + i);
		this.networkPack(data, 150);
	}

	private int inventoryFingerprint() {
		int hash = 1;
		for(net.minecraft.item.ItemStack stack : slots) {
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
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "heavy");
		tanks[2].readFromNBT(nbt, "reformate");
		tanks[3].readFromNBT(nbt, "light");
		tanks[4].readFromNBT(nbt, "gas");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		tanks[0].writeToNBT(nbt, "input");
		tanks[1].writeToNBT(nbt, "heavy");
		tanks[2].writeToNBT(nbt, "reformate");
		tanks[3].writeToNBT(nbt, "light");
		tanks[4].writeToNBT(nbt, "gas");
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 1,
					yCoord,
					zCoord - 1,
					xCoord + 2,
					yCoord + 9,
					zCoord + 2
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
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public FluidTank[] getAllTanks() {
		return tanks;
	}

	@Override
	public FluidTank[] getSendingTanks() {
		return new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4]};
	}

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] {tanks[0]};
	}

	@Override
	public boolean canConnect(ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public boolean canConnect(FluidType type, ForgeDirection dir) {
		return dir != ForgeDirection.UNKNOWN && dir != ForgeDirection.DOWN;
	}

	@Override
	public void writeNBT(NBTTagCompound nbt) {
		if(tanks[0].getFill() == 0 && tanks[1].getFill() == 0 && tanks[2].getFill() == 0 && tanks[3].getFill() == 0 && tanks[4].getFill() == 0) return;
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < 5; i++) this.tanks[i].writeToNBT(data, "" + i);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < 5; i++) this.tanks[i].readFromNBT(data, "" + i);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineVacuumDistill(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineVacuumDistill(player.inventory, this);
	}
}
