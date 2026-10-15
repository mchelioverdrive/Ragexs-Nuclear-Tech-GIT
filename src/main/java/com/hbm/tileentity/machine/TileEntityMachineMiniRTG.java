package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import net.minecraft.item.ItemStack;
import com.hbm.blocks.ModBlocks;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.tile.IInfoProviderEC;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineMiniRTG extends TileEntityLoadedBase implements api.hbm.energymk2.IRatedGenerator, IInfoProviderEC, com.hbm.tileentity.IPersistentNBT, com.hbm.realsim.IMachineInfo {
    private final com.hbm.realsim.OutputBudget outputBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getOutputBudget() { return outputBudget; }
    @Override public long getRatedOutputWatts() { return Math.max(10L, ((getSourceThermalWatts() + 199) / 200) * 10L); }

	private static final int TASK_GENERATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;

    public ItemStack source;
    private NBTTagCompound unresolvedSource;
    public boolean hasUnresolvedSource() { return unresolvedSource != null; }
    public boolean legacySource;
    public final com.hbm.realsim.IsotopeLedger isotope = new com.hbm.realsim.IsotopeLedger();
    private void ensureSource() {
        if(source == null && unresolvedSource == null) {
            source = new net.minecraft.item.ItemStack(worldObj != null && getBlockType() == ModBlocks.machine_powerrtg ? com.hbm.items.ModItems.pellet_rtg_polonium : com.hbm.items.ModItems.pellet_rtg);
            legacySource = true;
        }
    }
    public long getSourceThermalWatts() { ensureSource(); return com.hbm.realsim.IsotopeLedger.thermalWatts(source); }
    public static net.minecraft.item.ItemStack crafted(net.minecraft.block.Block block, net.minecraft.item.Item pellet) {
        net.minecraft.item.ItemStack result = new net.minecraft.item.ItemStack(block);
        NBTTagCompound root = new NBTTagCompound(), data = new NBTTagCompound(), fuel = new NBTTagCompound();
        new net.minecraft.item.ItemStack(pellet).writeToNBT(fuel);
        data.setTag("isotopeSource", fuel); root.setTag(NBT_PERSISTENT_KEY, data); result.setTagCompound(root);
        return result;
    }

	public long energyQuanta;
	boolean tact = false;
	
	@Override
	public void updateEntity() {
		// Generation and export are driven by MachineRuntime.
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		runtimeInitialized = true;
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_GENERATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.providePowerToDirectReceiver(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
		runtimeEnergyMutation = true;
		try {
			long now = worldObj.getTotalWorldTime();
            if(isotope.begin(now)) {
                ensureSource();
                long watts = getSourceThermalWatts();
                if(source != null && source.getItem() instanceof com.hbm.items.machine.ItemRTGPellet) source = com.hbm.items.machine.ItemRTGPellet.handleDecay(source, (com.hbm.items.machine.ItemRTGPellet) source.getItem());
                setStoredEnergyQuanta(energyQuanta + isotope.convert(now, watts, 0, com.hbm.realsim.GeneratorContract.room(energyQuanta, getEnergyCapacityQuanta()), getRatedOutputWatts() / 10));
                markDirty();
            }

		} finally {
			runtimeEnergyMutation = false;
		}
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_GENERATE, TASK_SLOT_MAIN);
	}

	private void updatePowerConnections() {
		for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			this.registerPowerConnection(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
	}
	
    public long getOutput() { return isotope.electricQuanta; }
    public long getPowerOutputWatts() { return getOutput() * 10; }
    @Override public long getEnergyCapacityQuanta() { return com.hbm.realsim.GeneratorContract.buffer(getRatedOutputWatts()); }

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
        this.markDirty();
		if(!runtimeEnergyMutation) this.markMachineDirty(MachineDirtyCause.ENERGY);
	}


	@Override
	public void provideExtraInfo(NBTTagCompound data) {
		data.setBoolean(CompatEnergyControl.B_ACTIVE, true);
		data.setDouble(CompatEnergyControl.D_OUTPUT_HE, EnergyUnits.quantaToLegacyHe(this.getOutput()));
	}
    @Override public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt); outputBudget.read(nbt); readSource(nbt);
        energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt); outputBudget.write(nbt); writeSource(nbt);
        EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
    }
    private void readSource(NBTTagCompound tag) {
        if(tag.hasKey("isotopeSource")) {
            NBTTagCompound raw = tag.getCompoundTag("isotopeSource");
            source = ItemStack.loadItemStackFromNBT(raw);
            unresolvedSource = source == null ? (NBTTagCompound) raw.copy() : null;
        }
        legacySource = tag.getBoolean("legacySource"); isotope.read(tag);
    }
    private void writeSource(NBTTagCompound tag) {
        ensureSource(); NBTTagCompound fuel = unresolvedSource == null ? new NBTTagCompound() : (NBTTagCompound) unresolvedSource.copy();
        if(source != null) source.writeToNBT(fuel);
        tag.setTag("isotopeSource", fuel); tag.setBoolean("legacySource", legacySource); isotope.write(tag);
    }
    @Override public void writeNBT(NBTTagCompound tag) {
        NBTTagCompound data = new NBTTagCompound(); writeSource(data); EnergyUnits.writeEnergyQuanta(data, energyQuanta); tag.setTag(NBT_PERSISTENT_KEY, data);
    }
    @Override public void readNBT(NBTTagCompound tag) {
        NBTTagCompound data = tag.getCompoundTag(NBT_PERSISTENT_KEY); readSource(data); energyQuanta = EnergyUnits.readEnergyQuanta(data, "power");
    }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Sealed isotope source; 5% thermoelectric conversion");
        lines.add("Output falls as the isotope source decays");
        if(live) { lines.add("Current isotope heat: " + com.hbm.realsim.MachineInfo.thermalWatts(isotope.thermalQuanta * 10)); lines.add("Rejected heat: " + com.hbm.realsim.MachineInfo.thermalWatts(isotope.rejectedQuanta * 10)); }
    }
}
