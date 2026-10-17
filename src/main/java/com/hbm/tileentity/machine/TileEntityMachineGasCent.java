package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.blocks.BlockDummyable;
import com.hbm.inventory.container.ContainerMachineGasCent;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.gui.GUIMachineGasCent;
import com.hbm.inventory.recipes.GasCentrifugeRecipes;
import com.hbm.inventory.recipes.GasCentrifugeRecipes.PseudoFluidType;
import com.hbm.items.ModItems;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.inventory.recipes.loader.SerializableRecipe;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.LoopedSoundPacket;
import com.hbm.tileentity.IGUIProvider;
import com.hbm.tileentity.TileEntityMachineBase;
import com.hbm.util.BufferUtil;
import com.hbm.util.CompatEnergyControl;
import com.hbm.util.InventoryUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.energymk2.IBatteryItem;
import api.hbm.fluid.IFluidStandardTransceiver;
import com.hbm.realsim.ProcessRecipe;
import com.hbm.realsim.ProcessBatch;
import com.hbm.realsim.ProcessMachine;
import com.hbm.realsim.PhysicalRegistry;
import com.hbm.realsim.NuclearChemicalProcesses;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

//epic!
public class TileEntityMachineGasCent extends TileEntityMachineBase implements IEnergyReceiverMK2, IFluidStandardTransceiver, IGUIProvider, IInfoProviderEC, com.hbm.realsim.IMachineInfo {
	
	public final ProcessBatch nuclearBatch = new ProcessBatch();
    public final FluidTank recoveredFluorine = new FluidTank(Fluids.FLUORINE, 512).withDefaultCapacity();
	public long energyQuanta;
	public int progress;
	public boolean isProgressing;
	public static final int maxPower = 100000;
	public static final int processingSpeed = 150;
	
	public FluidTank tank;
	public PseudoFluidTank inputTank;
	public PseudoFluidTank outputTank;
	private ItemStack[] cachedEnrichmentOutputs;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private boolean runtimeBatteryMutation;
	private boolean runtimeMaterialEligible;
	private boolean runtimeSettling;
	private long lastAccountingTick = Long.MIN_VALUE;
	private long lastBatteryChargeTick = Long.MIN_VALUE;
	private long clientProgressTick;
	private int observedInventoryFingerprint;
	private boolean inventoryFingerprintInitialized;
	private PseudoFluidType observedInputType;
	private boolean lastSoundProgressing;
	private long observedRecipeRevision;
	private static final int TASK_PROCESS = 1;
	private static final int TASK_BATTERY = 2;
	private static final int TASK_TRANSFER = 3;
	private static final int TASK_SLOT_MAIN = 0;
	private static final int TASK_SLOT_BATTERY = 1;
	private static final int TASK_SLOT_TRANSFER = 2;
	
	private static final int[] slots_io = new int[] { 0, 1, 2, 3 };
	
	public TileEntityMachineGasCent() {
		super(7); 
		tank = new FluidTank(Fluids.UF6, 2000);
		inputTank = new PseudoFluidTank(PseudoFluidType.NUF6, 8000);
		outputTank = new PseudoFluidTank(PseudoFluidType.LEUF6, 8000);
		this.trackMachineFluidTank(tank);
        this.trackMachineFluidTank(recoveredFluorine);
	}
	
	@Override
	public String getName() {
		return "container.gasCentrifuge";
	}
	
	@Override
	public boolean canExtractItem(int i, ItemStack itemStack, int j) {
		return i < 4;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return slots_io;
	}
	
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		progress = nbt.getInteger("progress");
        nuclearBatch.read(nbt); recoveredFluorine.readFromNBT(nbt, "recoveredFluorine");

		runtimeInitialized = false;
		inventoryFingerprintInitialized = false;
		observedRecipeRevision = -1L;
		lastAccountingTick = Long.MIN_VALUE;
		lastBatteryChargeTick = Long.MIN_VALUE;
		tank.readFromNBT(nbt, "tank");
		inputTank.readFromNBT(nbt, "inputTank");
		outputTank.readFromNBT(nbt, "outputTank");
        if(!nbt.hasKey("realSimProcess") && NuclearChemicalProcesses.cascade(inputTank.getTankType().name, true) != null) progress = 0;
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("progress", progress);
        nuclearBatch.write(nbt); recoveredFluorine.writeToNBT(nbt, "recoveredFluorine");
		tank.writeToNBT(nbt, "tank");
		inputTank.writeToNBT(nbt, "inputTank");
		outputTank.writeToNBT(nbt, "outputTank");
	}
	
	public int getCentrifugeProgressScaled(int i) {
		long projected = progress;
		if(worldObj != null && worldObj.isRemote && isProgressing) projected += Math.max(0L, worldObj.getTotalWorldTime() - clientProgressTick);
		return (int) (Math.min(getProcessingSpeed(), projected) * i / getProcessingSpeed());
	}
	
	public long getPowerRemainingScaled(int i) {
		return (energyQuanta * i) / maxPower;
	}
	
    private boolean fastNuclear() { return slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed; }
    private ProcessRecipe selectedNuclearBill() { return NuclearChemicalProcesses.cascade(inputTank.getTankType().name, fastNuclear()); }
    private boolean isNuclear() { return nuclearBatch.active() || selectedNuclearBill() != null; }
    private boolean nuclearReady(ProcessRecipe bill, boolean requireFeed) {
        if(bill == null || requireFeed && inputTank.getFill() < bill.quantities[0]) return false;
        if(inputTank.getTankType().getIfHighSpeed() && !fastNuclear() && !nuclearBatch.active()) return false;
        ItemStack[] preview = slots.clone();
        for(int i = 0; i < 4; i++) if(preview[i] != null) preview[i] = preview[i].copy();
        for(int i = 0; i < bill.outputs.length; i++) {
            String name = bill.outputs[i]; int amount = Math.toIntExact(nuclearBatch.products.preview(name, bill.productMass[i]));
            if(name.startsWith("CASCADE_")) {
                PseudoFluidType type = PseudoFluidType.types.get(name.substring(8));
                if(type == null || outputTank.getFill() > 0 && outputTank.getTankType() != type || (long) outputTank.getFill() + amount > outputTank.getMaxFill()) return false;
            } else if(name.equals("FLUORINE")) {
                if((long) recoveredFluorine.getFill() + amount > recoveredFluorine.getMaxFill()) return false;
            } else if(amount > 0 && !ProcessMachine.add(preview, 0, 3, ProcessMachine.solid(name, amount))) return false;
        }
        return true;
    }
    private void settleNuclear() {
        runtimeSettling = true; runtimeEnergyMutation = true; this.beginMachineFluidMutation();
        try {
            ProcessRecipe bill = nuclearBatch.active() ? nuclearBatch.recipe() : selectedNuclearBill();
            if(bill == null) return;
            inputTank.maxFluid = bill.inputCapacities[0]; tank.resizePreservingContents(Math.max(400, bill.inputCapacities[0]));
            for(int i = 0; i < bill.outputs.length; i++) {
                if(bill.outputs[i].equals("FLUORINE")) recoveredFluorine.resizePreservingContents(bill.outputCapacities[i]);
                else if(bill.outputs[i].startsWith("CASCADE_")) outputTank.maxFluid = bill.outputCapacities[i];
            }
            if(!nuclearBatch.active()) {
                if(!nuclearReady(bill, true)) { isProgressing = false; return; }
                nuclearBatch.reserve(bill); inputTank.setFill(inputTank.getFill() - bill.quantities[0]); markDirty();
            }
            if(!nuclearReady(bill, false)) { isProgressing = false; return; }
            long now = worldObj.getTotalWorldTime(), work = nuclearBatch.nextWork();
            isProgressing = work <= (fastNuclear() ? 10000 : 2000) && nuclearBatch.canAdvance(now, energyQuanta, 0);
            if(isProgressing) {
                setStoredEnergyQuanta(energyQuanta - work); nuclearBatch.advance(now); nuclearBatch.recordDraw(now, work);
                if(nuclearBatch.complete()) {
                    int[] amount = nuclearBatch.finish();
                    for(int i = 0; i < amount.length; i++) {
                        String name = bill.outputs[i];
                        if(name.startsWith("CASCADE_")) {
                            outputTank.setTankType(PseudoFluidType.types.get(name.substring(8)));
                            outputTank.setFill(outputTank.getFill() + amount[i]);
                        } else if(name.equals("FLUORINE")) recoveredFluorine.setFill(recoveredFluorine.getFill() + amount[i]);
                        else if(amount[i] > 0 && !ProcessMachine.add(slots, 0, 3, ProcessMachine.solid(name, amount[i]))) throw new IllegalStateException("Cascade output changed during commit");
                    }
                }
                progress = nuclearBatch.ticks(); markDirty(); markNetworkDirty();
            }
        } finally { this.endMachineFluidMutation(); runtimeEnergyMutation = false; runtimeSettling = false; }
    }
    @Override public long getMachineDemandWatts() { ProcessRecipe bill = nuclearBatch.active() ? nuclearBatch.recipe() : selectedNuclearBill(); return bill == null ? 3000 : bill.electricalRatingWatts; }
    @Override public long getMachineCurrentDrawWatts() { return isNuclear() ? nuclearBatch.currentDrawWatts(worldObj == null ? Long.MIN_VALUE : worldObj.getTotalWorldTime()) : isProgressing ? 3000 : 0; }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        com.hbm.realsim.MachineInfo.process(lines, nuclearBatch.active() ? nuclearBatch.recipe() : selectedNuclearBill());
        lines.add("Carrier recovery exports fluorine; all coproducts apply backpressure");
        lines.add("Isotope quantities are fixed carrier aliquots, not standard gas liters");
        lines.add("Power draw: 20 kW; speed upgrade: 100 kW, same energy per batch");
    }

	private boolean canEnrich() {
        if(isNuclear()) return nuclearBatch.active() || energyQuanta > 0 && nuclearReady(selectedNuclearBill(), true);
		if(runtimeMaterialEligible) this.refreshEnrichmentEligibility();
		return energyQuanta > 0L && runtimeMaterialEligible;
	}

	private boolean hasEnrichmentMaterials() {
        if(isNuclear()) return nuclearBatch.active() || nuclearReady(selectedNuclearBill(), true);
		if(inputTank.getFill() < inputTank.getTankType().getFluidConsumed()
				|| outputTank.getFill() + inputTank.getTankType().getFluidProduced() > outputTank.getMaxFill()) return false;
		if(inputTank.getTankType().getIfHighSpeed() && !(slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed)) return false;
		ItemStack[] outputs = cachedEnrichmentOutputs;
		return outputs != null && outputs.length > 0 && InventoryUtil.doesArrayHaveSpace(slots, 0, 3, outputs);
	}
	
	private void enrich() {
		ItemStack[] output = cachedEnrichmentOutputs;
		
		this.progress = 0;
		inputTank.setFill(inputTank.getFill() - inputTank.getTankType().getFluidConsumed()); 
		outputTank.setFill(outputTank.getFill() + inputTank.getTankType().getFluidProduced()); 
		
		for(byte i = 0; i < output.length; i++)
			InventoryUtil.tryAddItemToInventory(slots, 0, 3, output[i].copy()); //reference types almost got me again
	}
	
	private void attemptConversion() {
		if(inputTank.getFill() < inputTank.getMaxFill() && tank.getFill() > 0) {
			int fill = Math.min(inputTank.getMaxFill() - inputTank.getFill(), tank.getFill());
			
			tank.setFill(tank.getFill() - fill);
			inputTank.setFill(inputTank.getFill() + fill);
		}
	}
	
	private boolean attemptTransfer(TileEntity te) {
		if(te instanceof TileEntityMachineGasCent) {
			TileEntityMachineGasCent cent = (TileEntityMachineGasCent) te;
			
			if(cent.tank.getFill() == 0 && cent.tank.getTankType() == tank.getTankType()) {
				if(cent.runtimeInitialized && cent.worldObj != null && !cent.worldObj.isRemote) cent.settleProgressThrough(cent.worldObj.getTotalWorldTime());
				int oldInputFill = cent.inputTank.getFill();
				PseudoFluidType oldInputType = cent.inputTank.getTankType();
				if(cent.inputTank.getTankType() != outputTank.getTankType() && (cent.inputTank.getFill() > 0 || cent.nuclearBatch.active())) return false;
                if(cent.inputTank.getTankType() != outputTank.getTankType() && outputTank.getTankType() != PseudoFluidType.NONE) {
					cent.inputTank.setTankType(outputTank.getTankType());
				}
				
				//God, why did I forget about the entirety of the fucking math library?
				if(cent.inputTank.getFill() < cent.inputTank.getMaxFill() && outputTank.getFill() > 0) {
					int fill = Math.min(cent.inputTank.getMaxFill() - cent.inputTank.getFill(), outputTank.getFill());
					
					outputTank.setFill(outputTank.getFill() - fill);
					cent.inputTank.setFill(cent.inputTank.getFill() + fill);
				}
				if(oldInputFill != cent.inputTank.getFill() || oldInputType != cent.inputTank.getTankType()) {
					cent.markDirty();
					cent.markMachineDirty(MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE | MachineDirtyCause.TOPOLOGY);
				}
				
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void updateEntity() {
		// Server work is driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_5 | MachineExecutionStrategy.COARSE_20 | MachineExecutionStrategy.COARSE_100;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		long now = worldObj.getTotalWorldTime();
		boolean wasProgressing = runtimeInitialized && isProgressing;
		if(lastAccountingTick == Long.MIN_VALUE) lastAccountingTick = now;
		else this.settleProgressThrough(now - 1L);
		runtimeInitialized = true;
		this.refreshRuntimeState();
		this.beginMachineFluidMutation();
		try { if(this.canConvert()) this.attemptConversion(); }
		finally { this.endMachineFluidMutation(); }
		this.refreshEnrichmentEligibility();
		PseudoFluidType inputType = inputTank.getTankType();
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.TOPOLOGY)) != 0 || observedInputType != inputType) this.updateConnections();
		observedInputType = inputType;
		observedRecipeRevision = SerializableRecipe.getRegistryRevision();
		if(wasProgressing) this.settleProgressThrough(now);
		else lastAccountingTick = now;
		this.evaluateAndSchedule(now);
		this.networkPackNT(50);
		this.sendSoundKeepalive();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		long now = worldObj.getTotalWorldTime();
		if(taskType == TASK_BATTERY && taskSlot == TASK_SLOT_BATTERY) {
			this.settleProgressThrough(now - 1L);
			if(lastBatteryChargeTick != now) this.chargeBattery(now);
			this.evaluateAndSchedule(now);
			if(runtimeMaterialEligible && energyQuanta > 0L) this.scheduleMachineTransition(now, TASK_PROCESS, TASK_SLOT_MAIN);
			if(now % 20L == 0L) this.networkPackNTIfDirty(50);
			return;
		} else if(taskType == TASK_PROCESS && taskSlot == TASK_SLOT_MAIN) {
			if(lastBatteryChargeTick != now && this.hasBatteryWork()) {
				this.scheduleMachineTransition(now, TASK_PROCESS, TASK_SLOT_MAIN);
				return;
			}
			this.settleProgressThrough(now);
		} else if(taskType == TASK_TRANSFER && taskSlot == TASK_SLOT_TRANSFER) {
			this.settleProgressThrough(now);
			this.transferOrDeconvert();
			this.beginMachineFluidMutation();
			try { if(this.canConvert()) this.attemptConversion(); }
			finally { this.endMachineFluidMutation(); }
			this.refreshEnrichmentEligibility();
		} else return;
		this.observeInventoryFingerprint();
		this.markDirty();
		this.markNetworkDirty();
		this.evaluateAndSchedule(now);
		this.networkPackNTIfDirty(50);
		this.sendSoundKeepalive();
	}

	private void chargeBattery(long now) {
		long oldEnergy = energyQuanta;
		runtimeEnergyMutation = true;
		runtimeBatteryMutation = true;
		try { this.setStoredEnergyQuanta(Library.chargeTEFromItems(slots, 4, energyQuanta, maxPower)); }
		finally { runtimeBatteryMutation = false; runtimeEnergyMutation = false; }
		lastBatteryChargeTick = now;
		if(oldEnergy != energyQuanta) this.markDirty();
	}

	private void settleProgressThrough(long target) {
		if(runtimeSettling || lastAccountingTick == Long.MIN_VALUE || target <= lastAccountingTick) return;
		long elapsed = target - lastAccountingTick;
		lastAccountingTick = target;
		if(isNuclear()) { settleNuclear(); return; }
		long remaining = elapsed;
		boolean changed = false;
		runtimeSettling = true;
		try {
			while(remaining > 0L) {
				if(!this.canEnrich()) {
					if(progress != 0 || isProgressing) changed = true;
					progress = 0;
					isProgressing = false;
					break;
				}
				int cost = slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed ? 300 : 200;
				long affordable = energyQuanta / cost;
				if(affordable <= 0L) {
					if(energyQuanta > 0L) {
						runtimeEnergyMutation = true;
						try { this.setStoredEnergyQuanta(0L); }
						finally { runtimeEnergyMutation = false; }
						remaining--;
					}
					progress = 0;
					isProgressing = false;
					changed = true;
					break;
				}
				long steps = Math.min(remaining, Math.min(affordable, Math.max(1, this.getProcessingSpeed() - progress)));
				runtimeEnergyMutation = true;
				try { this.setStoredEnergyQuanta(energyQuanta - steps * cost); }
				finally { runtimeEnergyMutation = false; }
				progress += (int) steps;
				remaining -= steps;
				isProgressing = true;
				changed = true;
				if(remaining > 0L && energyQuanta == 0L && progress < this.getProcessingSpeed()) {
					progress = 0;
					isProgressing = false;
					break;
				}
				if(progress >= this.getProcessingSpeed()) {
					progress = 0;
				this.enrich();
				this.beginMachineFluidMutation();
				try { if(this.canConvert()) this.attemptConversion(); }
				finally { this.endMachineFluidMutation(); }
				this.refreshEnrichmentEligibility();
				continue;
				}
				if(remaining > 0L && steps >= affordable) {
					progress = 0;
					isProgressing = false;
					break;
				}
			}
		} finally { runtimeSettling = false; }
		if(changed) { this.markDirty(); this.markNetworkDirty(); }
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(worldObj == null || worldObj.isRemote) return;
		if(cadence == 5) {
			if(this.observeInventoryFingerprint()) this.markMachineDirty(MachineDirtyCause.INVENTORY | MachineDirtyCause.RECIPE | MachineDirtyCause.ENERGY | MachineDirtyCause.CONFIGURATION);
		} else if(cadence == 20) {
			this.updateConnections();
            if(recoveredFluorine.getFill() > 0) for(DirPos pos : getConPos()) this.sendFluid(recoveredFluorine, worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			this.sendSoundKeepalive();
		} else if(cadence == 100) {
			long revision = SerializableRecipe.getRegistryRevision();
			if(revision != observedRecipeRevision) {
				observedRecipeRevision = revision;
				this.markMachineDirty(MachineDirtyCause.RECIPE);
			}
		}
	}

	private void refreshRuntimeState() {
		this.setTankType(5);
		cachedEnrichmentOutputs = inputTank.getTankType().getOutput();
		this.observeInventoryFingerprint();
	}

	private void refreshEnrichmentEligibility() {
		cachedEnrichmentOutputs = inputTank.getTankType().getOutput();
		runtimeMaterialEligible = this.hasEnrichmentMaterials();
	}

	private boolean canConvert() {
		return GasCentrifugeRecipes.fluidConversions.get(tank.getTankType()) == inputTank.getTankType() && inputTank.getFill() < inputTank.getMaxFill() && tank.getFill() > 0;
	}

	private boolean hasBatteryWork() {
		if(energyQuanta >= maxPower || slots[4] == null) return false;
		if(slots[4].getItem() == ModItems.battery_creative || slots[4].getItem() == ModItems.fusion_core_infinite) return true;
		if(!(slots[4].getItem() instanceof IBatteryItem)) return false;
		IBatteryItem battery = (IBatteryItem) slots[4].getItem();
		return battery.getMaxOutputQuantaPerTick() > 0 && battery.getStoredEnergyQuanta(slots[4]) > 0;
	}

	private void transferOrDeconvert() {
        ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
        this.attemptTransfer(worldObj.getTileEntity(xCoord - dir.offsetX, yCoord, zCoord - dir.offsetZ));
    }

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
        if(isNuclear()) {
            if(this.canEnrich()) this.scheduleMachineTransition(now + 1L, TASK_PROCESS, TASK_SLOT_MAIN);
            else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
            if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
            else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
            if(outputTank.getFill() > 0) this.scheduleMachineTransition(now + 10L - now % 10L, TASK_TRANSFER, TASK_SLOT_TRANSFER);
            else this.cancelMachineTransition(TASK_TRANSFER, TASK_SLOT_TRANSFER);
            return;
        }
		if(this.canEnrich() || progress > 0) {
			int cost = slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed ? 300 : 200;
			long needed = Math.max(1L, this.getProcessingSpeed() - progress);
			long affordable = energyQuanta / cost;
			long resourceBoundary = affordable >= needed ? Long.MAX_VALUE : affordable + 1L;
			long delay = Math.max(1L, Math.min(needed, resourceBoundary));
			this.scheduleMachineTransition(now + delay, TASK_PROCESS, TASK_SLOT_MAIN);
		} else this.cancelMachineTransition(TASK_PROCESS, TASK_SLOT_MAIN);
		if(this.hasBatteryWork()) this.scheduleMachineTransition(now + 1L, TASK_BATTERY, TASK_SLOT_BATTERY);
		else this.cancelMachineTransition(TASK_BATTERY, TASK_SLOT_BATTERY);
		if(outputTank.getFill() > 0) this.scheduleMachineTransition(now + 10L - now % 10L, TASK_TRANSFER, TASK_SLOT_TRANSFER);
		else this.cancelMachineTransition(TASK_TRANSFER, TASK_SLOT_TRANSFER);
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
			if(GasCentrifugeRecipes.fluidConversions.containsValue(inputTank.getTankType())) this.trySubscribe(tank.getTankType(), worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	private void sendSoundKeepalive() {
		if(isProgressing != lastSoundProgressing) {
			lastSoundProgressing = isProgressing;
			PacketDispatcher.wrapper.sendToAllAround(new LoopedSoundPacket(xCoord, yCoord, zCoord), new TargetPoint(worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 50));
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
		buf.writeLong(energyQuanta);
		buf.writeInt(progress);
		buf.writeBoolean(isProgressing);
		buf.writeLong(worldObj == null ? 0L : worldObj.getTotalWorldTime());
		//pseudofluids can be refactored another day
		buf.writeInt(inputTank.getFill());
		buf.writeInt(outputTank.getFill());
		BufferUtil.writeString(buf, inputTank.getTankType().name); //cough cough
		BufferUtil.writeString(buf, outputTank.getTankType().name);
		
		tank.serialize(buf);
        recoveredFluorine.serialize(buf);
        buf.writeInt(inputTank.maxFluid); buf.writeInt(outputTank.maxFluid);
	}
	
	@Override
	public void deserialize(ByteBuf buf) {
		super.deserialize(buf);
		energyQuanta = buf.readLong();
		progress = buf.readInt();
		isProgressing = buf.readBoolean();
		clientProgressTick = buf.readLong();
		
		inputTank.setFill(buf.readInt());
		outputTank.setFill(buf.readInt());
		inputTank.type = PseudoFluidType.types.get(BufferUtil.readString(buf));
		outputTank.type = PseudoFluidType.types.get(BufferUtil.readString(buf));
        if(inputTank.type == null) inputTank.type = PseudoFluidType.NONE;
        if(outputTank.type == null) outputTank.type = PseudoFluidType.NONE;
		
		tank.deserialize(buf);
        recoveredFluorine.deserialize(buf);
        inputTank.maxFluid = buf.readInt(); outputTank.maxFluid = buf.readInt();
	}
	
	private DirPos[] getConPos() {
		return new DirPos[] {
			new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y),
			new DirPos(xCoord + 1, yCoord, zCoord, Library.POS_X),
			new DirPos(xCoord - 1, yCoord, zCoord, Library.NEG_X),
			new DirPos(xCoord, yCoord, zCoord + 1, Library.POS_Z),
			new DirPos(xCoord, yCoord, zCoord - 1, Library.NEG_Z)
		};
	}

	@Override
	public void prepareEnergyTransfer() {
		if(!runtimeEnergyMutation && !runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		if(!runtimeEnergyMutation && !runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		this.energyQuanta = i;
		this.markPowerNetDirty();
		if(!runtimeBatteryMutation) this.markNetworkDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
		
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override protected void beforeInventorySlotChanged(int slot) {
		if(!runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override protected void beforeFluidStorageChanged(FluidTank changedTank) {
		if((changedTank == tank || changedTank == recoveredFluorine) && !runtimeSettling && runtimeInitialized && worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
	}

	@Override public void onChunkUnload() {
		if(worldObj != null && !worldObj.isRemote) this.settleProgressThrough(worldObj.getTotalWorldTime() - 1L);
		lastAccountingTick = Long.MIN_VALUE;
		lastBatteryChargeTick = Long.MIN_VALUE;
		super.onChunkUnload();
	}
	
	public int getProcessingSpeed() {
        ProcessRecipe nuclear = nuclearBatch.active() ? nuclearBatch.recipe() : selectedNuclearBill();
        if(nuclear != null) return nuclear.duration;
		if(slots[6] != null && slots[6].getItem() == ModItems.upgrade_gc_speed) {
			return processingSpeed - 70;
		}
		return processingSpeed;
	}
	
	public void setTankType(int in) {
		
		if(slots[in] != null && slots[in].getItem() instanceof IItemFluidIdentifier) {
			IItemFluidIdentifier id = (IItemFluidIdentifier) slots[in].getItem();
			FluidType newType = id.getType(worldObj, xCoord, yCoord, zCoord, slots[in]);
			
			if(tank.getTankType() != newType && tank.getFill() == 0 && inputTank.getFill() == 0 && outputTank.getFill() == 0 && !nuclearBatch.active()) {
				PseudoFluidType pseudo = GasCentrifugeRecipes.fluidConversions.get(newType);
				
				if(pseudo != null) {
					inputTank.setTankType(pseudo);
					outputTank.setTankType(pseudo.getOutputType());
					tank.setTankType(newType);
				}
			}
			
		}
	}
	
	@Override
	public boolean isFluidDemandObservable() { return true; }

	@Override
	public FluidTank[] getReceivingTanks() {
		return new FluidTank[] { tank };
	}

	@Override
	public FluidTank[] getAllTanks() {
		return new FluidTank[] { tank, recoveredFluorine };
	}
	@Override public FluidTank[] getSendingTanks() { return new FluidTank[] {recoveredFluorine}; }
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(xCoord, yCoord, zCoord, xCoord + 1, yCoord + 5, zCoord + 1);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
	
	public class PseudoFluidTank {
		PseudoFluidType type;
		int fluid;
		int maxFluid;
		
		public PseudoFluidTank(PseudoFluidType type, int maxFluid) {
			this.type = type;
			this.maxFluid = maxFluid;
		}
		
		public void setFill(int i) {
			fluid = i;
		}
		
		public void setTankType(PseudoFluidType type) {
			
			if(this.type == type || fluid > 0) return;
			
			if(type == null)
				this.type = PseudoFluidType.NONE;
			else
				this.type = type;
			
		}
		
		public PseudoFluidType getTankType() {
			return type;
		}
		
		public int getFill() {
			return fluid;
		}
		
		public int getMaxFill() {
			return maxFluid;
		}
		
		//Called by TE to save fillstate
		public void writeToNBT(NBTTagCompound nbt, String s) {
			nbt.setInteger(s, fluid);
			nbt.setInteger(s + "_max", maxFluid);
			nbt.setString(s + "_type", type.name);
		}
		
		//Called by TE to load fillstate
		public void readFromNBT(NBTTagCompound nbt, String s) {
			fluid = nbt.getInteger(s);
			int max = nbt.getInteger(s + "_max");
			if(max > 0) maxFluid = nbt.getInteger(s + "_max");
			type = PseudoFluidType.types.get(nbt.getString(s + "_type"));
			if(type == null) type = PseudoFluidType.NONE;
		}
		
		/*        ______      ______
		 *       _I____I_    _I____I_
		 *      /      \\\  /      \\\
		 *     |IF{    || ||     } || |
		 *     | IF{   || ||    }  || |
		 *     |  IF{  || ||   }   || |
		 *     |   IF{ || ||  }    || |
		 *     |    IF{|| || }     || |
		 *     |       || ||       || |
		 *     |     } || ||IF{    || |
		 *     |    }  || || IF{   || |
		 *     |   }   || ||  IF{  || |
		 *     |  }    || ||   IF{ || |
		 *     | }     || ||    IF{|| |
		 *     |IF{    || ||     } || |
		 *     | IF{   || ||    }  || |
		 *     |  IF{  || ||   }   || |
		 *     |   IF{ || ||  }    || |
		 *     |    IF{|| || }     || |
		 *     |       || ||       || |
		 *     |     } || ||IF{	   || |
		 *     |    }  || || IF{   || |
		 *     |   }   || ||  IF{  || |
		 *     |  }    || ||   IF{ || |
		 *     | }     || ||    IF{|| |
		 *     |IF{    || ||     } || |
		 *     | IF{   || ||    }  || |
		 *     |  IF{  || ||   }   || |
		 *     |   IF{ || ||  }    || |
		 *     |    IF{|| || }     || |
		 *     |       || ||       || |
		 *     |     } || ||IF{	   || |
		 *     |    }  || || IF{   || |
		 *     |   }   || ||  IF{  || |
		 *     |  }    || ||   IF{ || |
		 *     | }     || ||    IF{|| |
		 *    _|_______||_||_______||_|_
		 *   |                          |
		 *   |                          |
		 *   |       |==========|       |
		 *   |       |NESTED    |       |
		 *   |       |IF  (:    |       |
		 *   |       |STATEMENTS|       |
		 *   |       |==========|       |
		 *   |                          |
		 *   |                          |
		 *   ----------------------------
		 */
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineGasCent(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineGasCent(player.inventory, this);
	}

	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, this.progress > 0);
		data.setInteger(CompatEnergyControl.I_PROGRESS, this.progress);
	}
}
