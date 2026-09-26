package com.hbm.tileentity.machine;

import com.hbm.tileentity.TileEntityTickingBase;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.EnumSkyBlock;

public class TileEntitySolarMirror extends TileEntityTickingBase {

	private static final int TASK_HEAT_TRANSFER = 1;

	public int tX;
	public int tY;
	public int tZ;
	public boolean isOn;
	private int sunIntensity;

	@Override
	public String getInventoryName() {
		return null;
	}

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		refreshSunState();
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence != 20) return;
		if(!isOn) refreshSunState();
		this.sendUpdate();
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_HEAT_TRANSFER || !isOn || worldObj == null || worldObj.isRemote) return;
		int sun = getCurrentSunIntensity();
		if(sun <= 0) {
			sunIntensity = 0;
			isOn = false;
			return;
		}
		sunIntensity = sun;

		TileEntity te = worldObj.getTileEntity(tX, tY - 1, tZ);
		if(te instanceof TileEntitySolarBoiler) {
			((TileEntitySolarBoiler) te).heat += sunIntensity;
		}
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_HEAT_TRANSFER, 0);
	}

	private void refreshSunState() {
		if(worldObj == null || worldObj.isRemote) return;
		int sun = getCurrentSunIntensity();
		if(sun <= 0) {
			sunIntensity = 0;
			isOn = false;
			this.cancelMachineTransition(TASK_HEAT_TRANSFER, 0);
			return;
		}

		sunIntensity = sun;
		isOn = true;
		this.scheduleMachineTransition(worldObj.getTotalWorldTime() + 1L, TASK_HEAT_TRANSFER, 0);
	}

	private int getCurrentSunIntensity() {
		if(tY < yCoord) return 0;
		int sun = worldObj.getSavedLightValue(EnumSkyBlock.Sky, xCoord, yCoord, zCoord) - worldObj.skylightSubtracted - 11;
		return sun > 0 && worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord) ? sun : 0;
	}

	@Override
	public void updateEntity() {
		if(worldObj.isRemote) {
			
			TileEntity te = worldObj.getTileEntity(tX, tY - 1, tZ);
			
			if(isOn && te instanceof TileEntitySolarBoiler) {
				TileEntitySolarBoiler boiler = (TileEntitySolarBoiler)te;
				boiler.primary.add(new ChunkCoordinates(xCoord, yCoord, zCoord));
			}
			
			if(worldObj.getTotalWorldTime() % 20 == 0)
				worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
		}
	}

	public void sendUpdate() {

		NBTTagCompound data = new NBTTagCompound();
		data.setInteger("posX", tX);
		data.setInteger("posY", tY);
		data.setInteger("posZ", tZ);
		data.setBoolean("isOn", isOn);
		this.networkPack(data, 200);
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		tX = nbt.getInteger("posX");
		tY = nbt.getInteger("posY");
		tZ = nbt.getInteger("posZ");
		isOn = nbt.getBoolean("isOn");
	}
	
	public void setTarget(int x, int y, int z) {
		tX = x;
		tY = y;
		tZ = z;
		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION | MachineDirtyCause.ENVIRONMENT);
		this.sendUpdate();
	}

	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		tX = nbt.getInteger("targetX");
		tY = nbt.getInteger("targetY");
		tZ = nbt.getInteger("targetZ");
	}

	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setInteger("targetX", tX);
		nbt.setInteger("targetY", tY);
		nbt.setInteger("targetZ", tZ);
	}
	
	AxisAlignedBB bb = null;
	
	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		
		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 25,
					yCoord - 25,
					zCoord - 25,
					xCoord + 25,
					yCoord + 25,
					zCoord + 25
					);
		}
		
		return bb;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 65536.0D;
	}
}
