package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.entity.projectile.EntityCog;
import com.hbm.lib.Library;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.tile.IHeatSource;
import api.hbm.tile.IProcessHeatSource;
import com.hbm.realsim.OutputBudget;
import com.hbm.realsim.MachineInfo;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityStirling extends TileEntityLoadedBase implements INBTPacketReceiver, api.hbm.energymk2.IRatedGenerator, IConfigurableMachine, com.hbm.realsim.IMachineInfo {
	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;
	private final OutputBudget outputBudget = new OutputBudget();
	private long lastConversionTick = Long.MIN_VALUE;
	private long conversionRemainder;
	private long currentOutputQuanta;
	private long currentRejectedQuanta;
	private final net.minecraft.block.Block itemProfile;
    private int installedHeatIntake = -1, installedEfficiency = -1;
	public TileEntityStirling() { this(null); }
	public TileEntityStirling(net.minecraft.block.Block itemProfile) { this.itemProfile = itemProfile; }
	private net.minecraft.block.Block ownerBlock() { return worldObj == null ? itemProfile : getBlockType(); }
	@Override public OutputBudget getOutputBudget() { return outputBudget; }
	public int getConfiguredEfficiencyPpm() {
        if(worldObj != null && worldObj.isRemote && installedEfficiency >= 0) return installedEfficiency;
		double installed = getGeatMeta() == 1 ? efficiencySteel : efficiency;
		return Double.isFinite(installed) ? (int) (Math.max(0D, Math.min(1D, installed)) * 1000000D) : 0;
	}
	public long getRatedHeatInputWatts() { return Math.max(0L, (long) maxHeat()) * 10L; }
	@Override public long getRatedOutputWatts() { return Math.max(0L, (long) maxHeat()) * getConfiguredEfficiencyPpm() / 1000000L * 10L; }
	public int getOperatingEfficiencyPpm(int sourceC) {
		if(sourceC <= 20) return 0;
		int carnot = (int) (1000000D * (1D - 293.15D / (sourceC + 273.15D)));
		return Math.min(getConfiguredEfficiencyPpm(), Math.max(0, carnot));
	}

	public long powerBuffer;
	public int heat;
	private int warnCooldown = 0;
	private int overspeed = 0;
	public boolean hasCog = true;

	public float spin;
	public float lastSpin;

	/* CONFIGURABLE CONSTANTS */
	public static double diffusion = 0.1D;
	public static double efficiency = 0.15D;
	public static double efficiencySteel = 0.20D;
	public static int maxHeatNormal = 6667;
	public static int maxHeatSteel = 20000;
	public static int overspeedLimit = 300;

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {

			float momentum = isCreative() ? powerBuffer * 50F / Math.max(1F, maxHeat()) : currentOutputQuanta * 45F / Math.max(1F, getRatedOutputWatts() / 10F);

			if(this.isCreative()) momentum = Math.min(momentum, 45F);

			this.lastSpin = this.spin;
			this.spin += momentum;

			if(this.spin >= 360F) {
				this.spin -= 360F;
				this.lastSpin -= 360F;
			}
		}
	}

	@Override public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		if((causes & MachineDirtyCause.LIFECYCLE) != 0) this.updatePowerConnections();
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		try {
			long now = worldObj.getTotalWorldTime();
			if(now <= lastConversionTick) { this.evaluateAndSchedule(now); return; }
			lastConversionTick = now;
			currentOutputQuanta = 0;
			currentRejectedQuanta = 0;
			heat = 0;
			if(hasCog) {
				if(isCreative()) this.setStoredEnergyQuanta(0);
				tryPullHeat();
				if(warnCooldown > 0) warnCooldown--;
				if(heat > maxHeat() && !isCreative()) {
					this.overspeed++;
					if(overspeed > 60 && warnCooldown == 0) warnCooldown = 100;
					if(overspeed > overspeedLimit) {
						this.setHasCog(false);
						this.worldObj.newExplosion(null, xCoord + 0.5, yCoord + 1, zCoord + 0.5, 5F, false, false);
						int orientation = this.getBlockMetadata() - BlockDummyable.offset;
						ForgeDirection dir = ForgeDirection.getOrientation(orientation);
						EntityCog cog = new EntityCog(worldObj, xCoord + 0.5 + dir.offsetX, yCoord + 1, zCoord + 0.5 + dir.offsetZ).setOrientation(orientation).setMeta(this.getGeatMeta());
						ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);
						cog.motionX = rot.offsetX;
						cog.motionY = 1 + (heat - maxHeat()) * 0.0001D;
						cog.motionZ = rot.offsetZ;
						worldObj.spawnEntityInWorld(cog);
						this.markDirty();
					}
				} else {
					this.overspeed = 0;
				}
			} else {
				this.overspeed = 0;
				this.warnCooldown = 0;
			}

			this.sendRuntimeState();
			if(hasCog) {
				this.providePowerToDirectReceiver(worldObj, xCoord + 2, yCoord, zCoord, Library.POS_X);
				this.providePowerToDirectReceiver(worldObj, xCoord - 2, yCoord, zCoord, Library.NEG_X);
				this.providePowerToDirectReceiver(worldObj, xCoord, yCoord, zCoord + 2, Library.POS_Z);
				this.providePowerToDirectReceiver(worldObj, xCoord, yCoord, zCoord - 2, Library.NEG_Z);
			}
		} finally {
			runtimeEnergyMutation = false;
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20 || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		this.updatePowerConnections();
		if(!hasCog) this.sendRuntimeState();
	}

	private void updatePowerConnections() {
		this.registerPowerConnection(worldObj, xCoord + 2, yCoord, zCoord, Library.POS_X);
		this.registerPowerConnection(worldObj, xCoord - 2, yCoord, zCoord, Library.NEG_X);
		this.registerPowerConnection(worldObj, xCoord, yCoord, zCoord + 2, Library.POS_Z);
		this.registerPowerConnection(worldObj, xCoord, yCoord, zCoord - 2, Library.NEG_Z);
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(hasCog) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, powerBuffer);
		data.setInteger("heat", heat);
		data.setBoolean("hasCog", hasCog);
		data.setLong("output", currentOutputQuanta);
		data.setLong("rejected", currentRejectedQuanta);
        data.setInteger("installedHeatIntake", maxHeat()); data.setInteger("installedEfficiency", getConfiguredEfficiencyPpm());
		INBTPacketReceiver.networkPack(this, data, 150);
	}

	public int getGeatMeta() {
		return ownerBlock() == null || ownerBlock() == ModBlocks.machine_stirling ? 0 : ownerBlock() == ModBlocks.machine_stirling_creative ? 2 : 1;
	}

	public int maxHeat() {
        if(worldObj != null && worldObj.isRemote && installedHeatIntake >= 0) return installedHeatIntake;
		return getGeatMeta() == 0 ? maxHeatNormal : maxHeatSteel;
	}

	public boolean isCreative() {
		return ownerBlock() != null && ownerBlock() == ModBlocks.machine_stirling_creative;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.heat = nbt.getInteger("heat");
		this.hasCog = nbt.getBoolean("hasCog");
		this.currentOutputQuanta = nbt.getLong("output");
		this.currentRejectedQuanta = nbt.getLong("rejected");
        installedHeatIntake = Math.max(0, nbt.getInteger("installedHeatIntake")); installedEfficiency = Math.max(0, Math.min(1000000, nbt.getInteger("installedEfficiency")));
	}

	protected void tryPullHeat() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		// Creative equipment retains its exempt, nominal heat-conversion behavior.
		if(isCreative()) {
			if(con instanceof IHeatSource) {
				IHeatSource source = (IHeatSource) con;
				heat = (int) (Math.max(0, source.getHeatStored()) * safeDiffusion());
				source.useUpHeat(heat);
				setStoredEnergyQuanta(heat);
				currentOutputQuanta = heat;
			}
			return;
		}
		if(!(con instanceof IProcessHeatSource)) return;
		IProcessHeatSource source = (IProcessHeatSource) con;
		int conversion = getOperatingEfficiencyPpm(source.getProcessTemperatureC());
		long room = Math.min(getRatedOutputWatts() / 10L, Math.max(0L, getEnergyCapacityQuanta() - powerBuffer));
		if(conversion <= 0 || room <= 0) return;
		long request = Math.max(0, maxHeat());
		request = Math.min(request, Math.max(0L, (room * 1000000L - conversionRemainder) / conversion));
		heat = (int) source.drawProcessHeatQuanta(worldObj.getTotalWorldTime(), request);
		long converted = (long) heat * conversion + conversionRemainder;
		currentOutputQuanta = converted / 1000000L;
		conversionRemainder = converted % 1000000L;
		currentRejectedQuanta = (long) heat * (1000000L - conversion) / 1000000L;
		if(heat > 0) markDirty();
		setStoredEnergyQuanta(powerBuffer + currentOutputQuanta);
	}
	private static double safeDiffusion() { return Double.isFinite(diffusion) ? Math.max(0D, Math.min(1D, diffusion)) : 0D; }

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "powerBuffer");
		this.hasCog = nbt.getBoolean("hasCog");
		this.overspeed = nbt.getInteger("overspeed");
		this.conversionRemainder = Math.floorMod(nbt.getLong("realSimStirlingRemainder"), 1000000L);
		this.lastConversionTick = nbt.hasKey("realSimStirlingTick") ? nbt.getLong("realSimStirlingTick") : Long.MIN_VALUE;
		outputBudget.read(nbt);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, powerBuffer);
		nbt.setBoolean("hasCog", hasCog);
		nbt.setInteger("overspeed", overspeed);
		nbt.setLong("realSimStirlingRemainder", conversionRemainder);
		nbt.setLong("realSimStirlingTick", lastConversionTick);
		outputBudget.write(nbt);
	}

	@Override
	public void setStoredEnergyQuanta(long power) {
		if(this.powerBuffer == power) return;
		this.powerBuffer = power;
		this.markPowerNetDirty();
		if(!runtimeEnergyMutation) this.markMachineEnergyDirty();
	}

	public void setHasCog(boolean hasCog) {
		if(this.hasCog == hasCog) return;
		this.hasCog = hasCog;
		this.markPowerNetDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return powerBuffer;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return isCreative() ? powerBuffer : Math.max(1L, getRatedOutputWatts() / 5L);
	}

	@Override
	public long getMaxOutputQuantaPerTick() {
		return !hasCog ? 0 : isCreative() ? Math.max(0, powerBuffer) : api.hbm.energymk2.IRatedGenerator.super.getMaxOutputQuantaPerTick();
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(currentOutputQuanta);
	}

	@Override public void extractEnergyQuanta(long amount) {
		if(isCreative()) { setStoredEnergyQuanta(powerBuffer - amount); return; }
		api.hbm.energymk2.IRatedGenerator.super.extractEnergyQuanta(amount);
	}
	@Override public void appendMachineInfo(java.util.List<String> lines, boolean live) {
		if(isCreative()) { lines.add("Creative converter; excluded from the physical power ladder"); return; }
		lines.add("Thermal intake ceiling: " + MachineInfo.thermalWatts(getRatedHeatInputWatts()));
		lines.add(String.format(java.util.Locale.US, "Installed heat-to-electric efficiency: %.1f%%; limited by hot/cold temperature difference", getConfiguredEfficiencyPpm() / 10000D));
		lines.add("Requires a compatible heater below; integral radiator at 20 \u00b0C");
		lines.add("Heat intake is governed; repair a broken gear with a matching replacement");
		if(live) {
			lines.add("Current output: " + MachineInfo.watts(currentOutputQuanta * 10L));
			lines.add("Current thermal intake: " + MachineInfo.thermalWatts((long) heat * 10L));
			lines.add("Current heat rejection: " + MachineInfo.thermalWatts(currentRejectedQuanta * 10L));
			if(!hasCog) lines.add("Gear missing; right-click with the matching large gear");
		}
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
					yCoord + 2,
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
	public String getConfigName() {
		return "stirling";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		diffusion = IConfigurableMachine.grab(obj, "D:diffusion", diffusion);
		efficiency = IConfigurableMachine.grab(obj, "D:efficiency", efficiency);
		// An explicitly configured old common efficiency still applies to both gears.
		if(obj.has("D:efficiency")) efficiencySteel = efficiency;
		efficiencySteel = IConfigurableMachine.grab(obj, "D:efficiencySteel", efficiencySteel);
		maxHeatNormal = IConfigurableMachine.grab(obj, "I:maxHeatNormal", maxHeatNormal);
		maxHeatSteel = IConfigurableMachine.grab(obj, "I:maxHeatSteel", maxHeatSteel);
		overspeedLimit = IConfigurableMachine.grab(obj, "I:overspeedLimit", overspeedLimit);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("D:diffusion").value(diffusion);
		writer.name("D:efficiency").value(efficiency);
		writer.name("D:efficiencySteel").value(efficiencySteel);
		writer.name("I:maxHeatNormal").value(maxHeatNormal);
		writer.name("I:maxHeatSteel").value(maxHeatSteel);
		writer.name("I:overspeedLimit").value(overspeedLimit);
	}
}
