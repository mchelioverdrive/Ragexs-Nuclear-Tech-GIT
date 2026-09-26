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

import api.hbm.energymk2.IEnergyProviderMK2;
import api.hbm.tile.IHeatSource;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityStirling extends TileEntityLoadedBase implements INBTPacketReceiver, IEnergyProviderMK2, IConfigurableMachine {
	private static final int TASK_SIMULATE = 1;
	private static final int TASK_SLOT_MAIN = 0;
	private boolean runtimeInitialized;
	private boolean runtimeEnergyMutation;

	public long powerBuffer;
	public int heat;
	private int warnCooldown = 0;
	private int overspeed = 0;
	public boolean hasCog = true;

	public float spin;
	public float lastSpin;

	/* CONFIGURABLE CONSTANTS */
	public static double diffusion = 0.1D;
	public static double efficiency = 0.5D;
	public static int maxHeatNormal = 300;
	public static int maxHeatSteel = 1500;
	public static int overspeedLimit = 300;

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {

			float momentum = powerBuffer * 50F / ((float) maxHeat());

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
		runtimeInitialized = true;
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_SIMULATE || taskSlot != TASK_SLOT_MAIN || worldObj == null || worldObj.isRemote || !runtimeInitialized) return;
		runtimeEnergyMutation = true;
		try {
			if(hasCog) {
				this.setStoredEnergyQuanta(0);
				tryPullHeat();
				this.setStoredEnergyQuanta((long) (this.heat * (this.isCreative() ? 1 : this.efficiency)));
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
				this.tryProvide(worldObj, xCoord + 2, yCoord, zCoord, Library.POS_X);
				this.tryProvide(worldObj, xCoord - 2, yCoord, zCoord, Library.NEG_X);
				this.tryProvide(worldObj, xCoord, yCoord, zCoord + 2, Library.POS_Z);
				this.tryProvide(worldObj, xCoord, yCoord, zCoord - 2, Library.NEG_Z);
			} else if(this.powerBuffer > 0) {
				this.setStoredEnergyQuanta(this.powerBuffer - 1);
			}
			this.heat = 0;
		} finally {
			runtimeEnergyMutation = false;
		}
		this.evaluateAndSchedule(worldObj.getTotalWorldTime());
	}

	@Override public void onMachineCoarsePoll(int cadence) {
		if(cadence == 20 && worldObj != null && !worldObj.isRemote && runtimeInitialized && !hasCog && powerBuffer <= 0) this.sendRuntimeState();
	}

	private void evaluateAndSchedule(long now) {
		if(!runtimeInitialized) return;
		if(hasCog || powerBuffer > 0) this.scheduleMachineTransition(now + 1L, TASK_SIMULATE, TASK_SLOT_MAIN);
		else this.cancelMachineTransition(TASK_SIMULATE, TASK_SLOT_MAIN);
	}

	private void sendRuntimeState() {
		NBTTagCompound data = new NBTTagCompound();
		EnergyUnits.writeEnergyQuanta(data, powerBuffer);
		data.setInteger("heat", heat);
		data.setBoolean("hasCog", hasCog);
		INBTPacketReceiver.networkPack(this, data, 150);
	}

	public int getGeatMeta() {
		return this.getBlockType() == ModBlocks.machine_stirling ? 0 : this.getBlockType() == ModBlocks.machine_stirling_creative ? 2 : 1;
	}

	public int maxHeat() {
		return this.getBlockType() == ModBlocks.machine_stirling ? maxHeatNormal : maxHeatSteel;
	}

	public boolean isCreative() {
		return this.getBlockType() == ModBlocks.machine_stirling_creative;
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.heat = nbt.getInteger("heat");
		this.hasCog = nbt.getBoolean("hasCog");
	}

	protected void tryPullHeat() {
		TileEntity con = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);

		if(con instanceof IHeatSource) {
			IHeatSource source = (IHeatSource) con;
			int heatSrc = (int) (source.getHeatStored() * diffusion);

			if(heatSrc > 0) {
				source.useUpHeat(heatSrc);
				this.heat += heatSrc;
				return;
			}
		}

		this.heat = Math.max(this.heat - Math.max(this.heat / 1000, 1), 0);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.powerBuffer = EnergyUnits.readEnergyQuanta(nbt, "powerBuffer");
		this.hasCog = nbt.getBoolean("hasCog");
		this.overspeed = nbt.getInteger("overspeed");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, powerBuffer);
		nbt.setBoolean("hasCog", hasCog);
		nbt.setInteger("overspeed", overspeed);
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
		return powerBuffer;
	}

	@Override
	public long getMaxOutputQuantaPerTick() {
		return this.hasCog ? this.getEnergyCapacityQuanta() : 0;
	}

	public long getPowerOutputWatts() {
		return EnergyUnits.quantaPerTickToWatts(this.getMaxOutputQuantaPerTick());
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
		maxHeatNormal = IConfigurableMachine.grab(obj, "I:maxHeatNormal", maxHeatNormal);
		maxHeatSteel = IConfigurableMachine.grab(obj, "I:maxHeatSteel", maxHeatSteel);
		overspeedLimit = IConfigurableMachine.grab(obj, "I:overspeedLimit", overspeedLimit);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("D:diffusion").value(diffusion);
		writer.name("D:efficiency").value(efficiency);
		writer.name("I:maxHeatNormal").value(maxHeatNormal);
		writer.name("I:maxHeatSteel").value(maxHeatSteel);
		writer.name("I:overspeedLimit").value(overspeedLimit);
	}
}
