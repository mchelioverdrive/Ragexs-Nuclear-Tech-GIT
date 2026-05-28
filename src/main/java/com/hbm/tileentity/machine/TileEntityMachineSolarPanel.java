package com.hbm.tileentity.machine;

import com.hbm.dim.CelestialBody;
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

	private long power;
	private long maxpwr = 1_000;

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {

			for(ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
				tryProvide(worldObj, xCoord + dir.offsetX, yCoord + dir.offsetY, zCoord + dir.offsetZ, dir);
			}

			power += getOutput();

			if(power > maxpwr)
				power = maxpwr;
		}
	}

	private boolean isSunVisible() {

		if(worldObj.provider instanceof WorldProviderOrbit) {
			return true; // orbit handles lighting differently (or should later use eclipse system)
		}

		// must have open sky
		if(!worldObj.canBlockSeeTheSky(xCoord, yCoord + 1, zCoord))
			return false;

		// must be daytime AND sun above horizon
		float angle = worldObj.getCelestialAngle(1.0F);

		return angle > 0.25F && angle < 0.75F;
	}

	// was? Balanced around 100he/t on Earth
	//now just randomly gives solar power?
	public long getOutput() {

		if(!isSunVisible())
			return 0;

		float sunPower = worldObj.provider instanceof WorldProviderOrbit
			? ((WorldProviderOrbit) worldObj.provider).getSunPower()
			: CelestialBody.getBody(worldObj).getSunPower();

		float angle = worldObj.getCelestialAngle(1.0F);

		// proper daylight curve (0 at night, 1 at noon)
		float daylight = 1.0F - Math.abs(angle - 0.5F) * 2.0F;
		daylight = MathHelper.clamp_float(daylight, 0.0F, 1.0F);

		float base = 100.0F;

		return (long)(base * daylight * daylight * sunPower);
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		return TileEntity.INFINITE_EXTENT_AABB;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}

	@Override
	public long getMaxPower() {
		return maxpwr; //temp
	}
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		this.power = nbt.getLong("power");
		this.maxpwr = nbt.getLong("maxpwr");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setLong("power", power);
		nbt.setLong("maxpwr", maxpwr);
	}
}
