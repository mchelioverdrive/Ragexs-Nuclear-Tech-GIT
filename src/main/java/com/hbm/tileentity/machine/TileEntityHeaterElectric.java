package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.blocks.BlockDummyable;
import com.hbm.interfaces.ICopiable;
import com.hbm.main.MainRegistry;
import com.hbm.sound.AudioWrapper;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.util.CompatEnergyControl;

import api.hbm.energymk2.IEnergyReceiverMK2;
import api.hbm.tile.IHeatSource;
import api.hbm.tile.IInfoProviderEC;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityHeaterElectric extends TileEntityLoadedBase implements IHeatSource, IEnergyReceiverMK2, INBTPacketReceiver, ICopiable, IInfoProviderEC, api.hbm.tile.IProcessHeatSource, com.hbm.realsim.IMachineInfo {
    private final com.hbm.realsim.OutputBudget heatExportBudget = new com.hbm.realsim.OutputBudget();
    @Override public com.hbm.realsim.OutputBudget getProcessHeatBudget() { return heatExportBudget; }
    @Override public long getRatedProcessHeatWatts() { return 400000L; }

    private final com.hbm.realsim.HeatTransfer physicalIntake = new com.hbm.realsim.HeatTransfer();

	private static final int TASK_HEAT = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
    private long lastHeatTick = Long.MIN_VALUE;
	private boolean runtimeEnergyMutation;

	public long energyQuanta;
	public int heatEnergy;
	public boolean isOn;
	protected int setting = 0;
	public static final int maxHeatEnergy = 500_000;

	private AudioWrapper audio;

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {

			if(isOn) {

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

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		runtimeInitialized = true;
		this.refreshPowerSubscription();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
		this.sendRuntimeState();
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.refreshPowerSubscription();
		if(heatEnergy == 0 && setting == 0 && this.hasHeatSourceWithEnergy()) this.markMachineDirty(com.hbm.machine.MachineDirtyCause.ENVIRONMENT);
		this.sendRuntimeState();
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_HEAT || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
        long now = worldObj.getTotalWorldTime();
        if(now <= lastHeatTick) return;
        lastHeatTick = now;
		this.heatEnergy = Math.min(Math.toIntExact((long) this.heatEnergy * 999L / 1000L), maxHeatEnergy);
		this.tryPullHeat();
		this.isOn = false;
		if(setting > 0 && this.energyQuanta >= this.getConsumption() && this.heatEnergy < maxHeatEnergy) {
			runtimeEnergyMutation = true;
			try { this.setStoredEnergyQuanta(this.energyQuanta - this.getConsumption()); }
			finally { runtimeEnergyMutation = false; }
			this.heatEnergy = Math.min(this.heatEnergy + getHeatGen(), maxHeatEnergy);
			this.isOn = true;
		}
        this.markDirty();
		this.sendRuntimeState();
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	private void refreshPowerSubscription() {
		ForgeDirection dir = ForgeDirection.getOrientation(this.getBlockMetadata() - BlockDummyable.offset);
		this.trySubscribe(worldObj, xCoord + dir.offsetX * 3, yCoord, zCoord + dir.offsetZ * 3, dir);
	}

	private boolean hasHeatSourceWithEnergy() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		return con instanceof IHeatSource && ((IHeatSource) con).getHeatStored() > 0;
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(heatEnergy > 0 || setting > 0 || this.hasHeatSourceWithEnergy()) this.scheduleMachineTransition(now + 1L, TASK_HEAT, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_HEAT, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		data.setByte("s", (byte) this.setting);
		data.setInteger("h", this.heatEnergy);
		data.setBoolean("o", isOn);
		data.setBoolean("muffled", muffled);
		INBTPacketReceiver.networkPack(this, data, 25);
	}

	@Override
	public AudioWrapper createAudioLoop() {
		return MainRegistry.proxy.getLoopedSound("hbm:block.electricHum", xCoord, yCoord, zCoord, 0.25F, 7.5F, 1.0F, 20);
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
		this.setting = nbt.getByte("s");
		this.heatEnergy = nbt.getInteger("h");
		this.isOn = nbt.getBoolean("o");
		this.muffled = nbt.getBoolean("muffled");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
        lastHeatTick = nbt.hasKey("realSimHeatTick") ? nbt.getLong("realSimHeatTick") : Long.MIN_VALUE;
        heatExportBudget.read(nbt.getCompoundTag("realSimHeatExport")); physicalIntake.read(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.setting = nbt.getInteger("setting");
		this.heatEnergy = nbt.getInteger("heatEnergy");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
        nbt.setLong("realSimHeatTick", lastHeatTick);
        net.minecraft.nbt.NBTTagCompound export = new net.minecraft.nbt.NBTTagCompound(); heatExportBudget.write(export); physicalIntake.write(nbt); nbt.setTag("realSimHeatExport", export);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setInteger("setting", setting);
		nbt.setInteger("heatEnergy", heatEnergy);
	}

	protected void tryPullHeat() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);

		if(con instanceof api.hbm.tile.IProcessHeatSource && ((api.hbm.tile.IProcessHeatSource) con).getProcessTemperatureC() >= getProcessTemperatureC()) {
			api.hbm.tile.IProcessHeatSource source = (api.hbm.tile.IProcessHeatSource) con;
            long pulled = source.drawProcessHeatQuanta(worldObj.getTotalWorldTime(), Math.max(0, Math.min(20000, maxHeatEnergy - heatEnergy)));
            this.heatEnergy = Math.toIntExact(Math.addExact(heatEnergy, physicalIntake.accept(pulled, getIntakeEfficiencyPpm())));
		}
	}

	public void toggleSetting() {
		setting++;

		if(setting > 10)
			setting = 0;
		this.markMachineDirty(com.hbm.machine.MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	public long getConsumption() {
		return Math.max(0, Math.min(10, setting)) * 2000L;
	}

	public long getPowerRequirementWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.getConsumption());
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return getConsumption() * 20;
	}

	public int getResistiveEfficiencyPpm() { return 980000; }
	public int getIntakeEfficiencyPpm() { return 850000; }
	public int getHeatGen() {
		return Math.toIntExact(getConsumption() * getResistiveEfficiencyPpm() / 1000000L);
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	@Override
	public int getHeatStored() {
		return heatEnergy;
	}

	@Override
	public void useUpHeat(int heat) {
		int next = Math.max(0, this.heatEnergy - heat);
		if(next == this.heatEnergy) return;
		this.heatEnergy = next;
		this.markDirty();
		this.markMachineDirty(com.hbm.machine.MachineDirtyCause.ENVIRONMENT);
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
				yCoord + 1,
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
	public void provideExtraInfo(NBTTagCompound data) {
		data.setLong(CompatEnergyControl.D_CONSUMPTION_HE, EnergyUnits.quantaToLegacyHe(getConsumption()));
		data.setLong(CompatEnergyControl.L_ENERGY_TU, getHeatStored());
		data.setLong(CompatEnergyControl.D_OUTPUT_TU, getHeatGen());
	}

	@Override
	public NBTTagCompound getSettings(World world, int x, int y, int z) {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("setting", setting);
		return nbt;
	}

	@Override
	public void pasteSettings(NBTTagCompound nbt, int index, World world, EntityPlayer player, int x, int y, int z) {
		this.setting = nbt.getInteger("setting");
	}
    @Override public int getProcessTemperatureC() { return 900; }
    @Override public long getMachineDemandWatts() { return worldObj == null ? 10L * 2000L * api.hbm.energymk2.EnergyUnits.WATTS_PER_QUANTUM_PER_TICK : getPowerRequirementWatts(); }
    @Override public long getMachineCurrentDrawWatts() { return isOn ? getPowerRequirementWatts() : 0L; }
    @Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
        lines.add("Resistive heat plus qualified external heat intake; the thermal export ceiling includes both sources");
        lines.add(String.format(java.util.Locale.US, "Resistive efficiency: %.1f%%; imported heat transfer: %.1f%%", getResistiveEfficiencyPpm() / 10000D, getIntakeEfficiencyPpm() / 10000D));
    }
}
