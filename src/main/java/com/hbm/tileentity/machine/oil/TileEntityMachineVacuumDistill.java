package com.hbm.tileentity.machine.oil;

import api.hbm.energymk2.EnergyUnits;
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

public class TileEntityMachineVacuumDistill extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IPersistentNBT, IGUIProvider, IFluidCopiable, com.hbm.realsim.IMachineInfo {

	
    public final com.hbm.realsim.ProcessBatch physicalBatch = new com.hbm.realsim.ProcessBatch();
	public long energyQuanta;
	public static final long maxPower = 1_000_000;
	
	public FluidTank[] tanks;
	
	private AudioWrapper audio;
	private int audioTime;
	public boolean isOn;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeBatchMutation;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private long observedRecipeRevision;
	private com.hbm.realsim.ProcessRecipe cachedRecipe;
	private static final int TASK_ACCOUNTING = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_FLUID = 3;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_FLUID = 2;

	public TileEntityMachineVacuumDistill() {
		super(12);
		
		this.tanks = new FluidTank[5];
		this.tanks[0] = new FluidTank(Fluids.HEAVYOIL, 32_000).withDefaultCapacity();
		this.tanks[1] = new FluidTank(Fluids.HEAVYOIL_VACUUM, 8_000).withDefaultCapacity();
		this.tanks[2] = new FluidTank(Fluids.SMEAR, 8_000).withDefaultCapacity();
		this.tanks[3] = new FluidTank(Fluids.LIGHTOIL_VACUUM, 8_000).withDefaultCapacity();
		this.tanks[4] = new FluidTank(Fluids.LUBRICANT, 24_000).withDefaultCapacity();
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
        runtimeInitialized = true; this.refreshRuntimeState(); this.updateConnections(); this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
        if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
        if(taskType == TASK_FLUID) {
            this.beginMachineFluidMutation();
            try { this.unloadOutputContainers(); this.sendOutputFluids(); }
            finally { this.endMachineFluidMutation(); }
            this.evaluateAndSchedule(worldObj.getTotalWorldTime());
            return;
        }
        runtimeBatchMutation = true; runtimeEnergyMutation = true; this.beginMachineFluidMutation();
        try {
            this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 0, energyQuanta, maxPower));
            int previous = physicalBatch.ticks();
            com.hbm.realsim.ProcessMachine.step(this, physicalBatch, cachedRecipe, new FluidTank[] {tanks[0]}, new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4]}, slots, 0, -1, 0, -1);
            isOn = physicalBatch.ticks() != previous;
        } finally { this.endMachineFluidMutation(); runtimeEnergyMutation = false; runtimeBatchMutation = false; }
        if(worldObj.getTotalWorldTime() % 20L == 0L) this.sendRuntimeState();
        this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}


	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
		} else if(cadence == 20) {
			this.updateConnections();
            this.sendRuntimeState();
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
        tanks[0].setType(11, slots); tanks[0].loadTank(1, 2, slots);
        cachedRecipe = RefineryRecipes.getPhysicalVacuum(tanks[0].getTankType());
        com.hbm.realsim.ProcessMachine.configure(physicalBatch, cachedRecipe, new FluidTank[] {tanks[1], tanks[2], tanks[3], tanks[4]});
        this.unloadOutputContainers(); this.observeInventoryFingerprint();
	}

	private boolean canRefine() {
        return physicalBatch.active() || com.hbm.realsim.ProcessMachine.inputsReady(cachedRecipe, new FluidTank[] {tanks[0]}, slots, 0, -1);
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
        this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY); this.cancelMachineTransition(TASK_FLUID, TASK_SLOT_FLUID);
        // Paid coproducts must keep draining after the last feed is consumed, including old saved reformate/sour gas.
        if(this.hasFluidOutput()) this.scheduleMachineTransition(now + 5L - Math.floorMod(now, 5L), TASK_FLUID, TASK_SLOT_FLUID);
        if(this.canRefine() || this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_ACCOUNTING, TASK_SLOT_MAIN);
        else { isOn = false; this.cancelMachineTransition(TASK_ACCOUNTING, TASK_SLOT_MAIN); }
	}


	private static long limitByOutput(long batches, FluidTank tank, int amountPerBatch) {
		return amountPerBatch <= 0 ? batches : Math.min(batches, (tank.getMaxFill() - tank.getFill()) / (long) amountPerBatch);
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
        physicalBatch.read(nbt);
		super.readFromNBT(nbt);
		runtimeInitialized = false;
		runtimeBatchMutation = false;

		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		tanks[0].readFromNBT(nbt, "input");
		tanks[1].readFromNBT(nbt, "heavy");
		tanks[2].readFromNBT(nbt, "reformate");
		tanks[3].readFromNBT(nbt, "light");
		tanks[4].readFromNBT(nbt, "gas");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
        physicalBatch.write(nbt);
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
	public boolean isFluidDemandObservable() { return true; }

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
        physicalBatch.write(nbt);
		NBTTagCompound data = new NBTTagCompound();
		for(int i = 0; i < 5; i++) this.tanks[i].writeToNBT(data, "" + i);
		nbt.setTag(NBT_PERSISTENT_KEY, data);
	}

	@Override
	public void readNBT(NBTTagCompound nbt) {
        physicalBatch.read(nbt);
		NBTTagCompound data = nbt.getCompoundTag(NBT_PERSISTENT_KEY);
		for(int i = 0; i < 5; i++) this.tanks[i].readFromNBT(data, "" + i);
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
	}

	@Override protected void beforeFluidStorageChanged(FluidTank tank) {
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

    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        com.hbm.realsim.MachineInfo.process(lines, physicalBatch.active() ? physicalBatch.recipe() : RefineryRecipes.getPhysicalVacuum(tanks[0].getTankType()));
        lines.add("Blocked operation: check feed state, paid utilities and product storage");
    }
}
