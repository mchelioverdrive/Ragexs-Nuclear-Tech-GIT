package com.hbm.tileentity.machine;

import api.hbm.energymk2.EnergyUnits;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.dim.orbit.WorldProviderOrbit;
import com.hbm.tileentity.TileEntityLoadedBase;

import api.hbm.energymk2.IEnergyProviderMK2;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.common.util.ForgeDirection;

public class TileEntityMachineSolarPanel extends TileEntityLoadedBase implements IEnergyProviderMK2 {

	private long energyQuanta;
	private long maxpwr = 1_000;

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {

			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				tryProvide(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			}

			this.setStoredEnergyQuanta(this.energyQuanta + getOutput());

			if(energyQuanta > maxpwr)
				this.setStoredEnergyQuanta(maxpwr);
		}
	}

	private boolean isSunVisible() {

		if(worldObj.provider instanceof WorldProviderOrbit) {
			return true;
		}

		if(!worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord))
			return false;

		if(!(worldObj.provider instanceof WorldProviderCelestial)) {
			long time = worldObj.getWorldTime() % 24000L;
			return time >= 0 && time < 12000;
		}

		float time =
			((WorldProviderCelestial) worldObj.provider)
				.getNormalizedDayTime();

		return time > 0.25F && time < 0.75F;
	}

	// was? Balanced around 100he/t on Earth
	//now just randomly gives solar power?
	public long getOutput() {

		if(!isSunVisible())
			return 0;

		float sunPower = worldObj.provider instanceof WorldProviderOrbit
			? ((WorldProviderOrbit) worldObj.provider).getSunPower()
			: CelestialBody.getBody(worldObj).getSunPower();

		float time;

		if(worldObj.provider instanceof WorldProviderCelestial) {
			time = ((WorldProviderCelestial) worldObj.provider)
				.getNormalizedDayTime();
		} else {
			time = worldObj.getCelestialAngle(1.0F);
		}

		// Convert sunrise->sunset (0 -> 0.5) into a noon peak
		float daylight = (float)Math.sin(time * Math.PI * 2.0F);
		daylight = MathHelper.clamp_float(daylight, 0.0F, 1.0F);

		float base = 100.0F;

		return (long)(base * daylight * daylight * sunPower);
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	public void setStoredEnergyQuanta(long energyQuanta) {
		if(this.energyQuanta == energyQuanta) return;
		this.energyQuanta = energyQuanta;
		this.markPowerNetDirty();
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxpwr; //temp
	}
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
		this.maxpwr = nbt.getLong("maxpwr");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
		nbt.setLong("maxpwr", maxpwr);
	}
}
