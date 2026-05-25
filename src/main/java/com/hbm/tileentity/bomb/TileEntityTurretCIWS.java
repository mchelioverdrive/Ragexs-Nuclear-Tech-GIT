package com.hbm.tileentity.bomb;

import api.hbm.energymk2.IEnergyReceiverMK2;
import com.hbm.main.MainRegistry;
import com.hbm.packet.AuxGaugePacket;
import com.hbm.packet.PacketDispatcher;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityTurretCIWS extends TileEntityTurretBase implements IEnergyReceiverMK2 {

	public int spin;
	public int rotation;
	private long power;
	private static final long MAX_POWER = 100_000;
	private static final long POWER_PER_SHOT = 250;

	@Override
	public void updateEntity() {

		super.updateEntity();

		this.ammo = 100;



		if(!worldObj.isRemote) {

			trySubscribe(
				worldObj,
				xCoord,
				yCoord - 1,
				zCoord,
				net.minecraftforge.common.util.ForgeDirection.UP
			);

			if(spin > 0)
				spin -= 1;

			rotation += spin;
			rotation = rotation % 360;

			PacketDispatcher.wrapper.sendToAll(new AuxGaugePacket(xCoord, yCoord, zCoord, rotation, 0));
		}
	}

	public void consumePower(long amount) {
		power = Math.max(0, power - amount);
	}

	@Override
	protected boolean canOperate() {
		return power >= 1_000;
	}

	@Override
	public long getPower() {
		return power;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		power = nbt.getLong("power");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setLong("power", power);
	}

	@Override
	public boolean isLoaded() {
		return true;
	}

	@Override
	public long getMaxPower() {
		return MAX_POWER;
	}

	@Override
	public void setPower(long power) {
		this.power = power;
	}
}
