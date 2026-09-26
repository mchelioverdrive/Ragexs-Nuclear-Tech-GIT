package com.hbm.tileentity.bomb;

import api.hbm.energymk2.EnergyUnits;
import api.hbm.energymk2.IEnergyReceiverMK2;
import com.hbm.inventory.recipes.GasCentrifugeRecipes;
import com.hbm.lib.Library;
import com.hbm.main.MainRegistry;
import com.hbm.packet.AuxGaugePacket;
import com.hbm.packet.PacketDispatcher;

import com.hbm.util.fauxpointtwelve.DirPos;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityTurretCIWS extends TileEntityTurretBase implements IEnergyReceiverMK2 {

	public int spin;
	public int rotation;
	private long energyQuanta;
	private static final long maxPower = 100_000;
	private static final long POWER_PER_SHOT = 250;
	public static final int consumption = 1000;

	//trySubscribe(
	//				worldObj,
	//				xCoord,
	//				yCoord - 1,
	//				zCoord,
	//				net.minecraftforge.common.util.ForgeDirection.UP
	//			);


	@Override
	protected boolean canOperate() {
		return hasPower();
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {
			updateConnections();
		}

		super.updateEntity();

		if(!worldObj.isRemote) {
			if(spin > 0)
				spin -= 1;

			rotation += spin;
			rotation = rotation % 360;

			PacketDispatcher.wrapper.sendToAll(new AuxGaugePacket(xCoord, yCoord, zCoord, rotation, 0));
		}
	}

	private DirPos[] getConPos() {
		return new DirPos[] {
			new DirPos(xCoord, yCoord - 1, zCoord, Library.NEG_Y)
			//we just want that
			//new DirPos(xCoord + 1, yCoord, zCoord, Library.POS_X),
			//new DirPos(xCoord - 1, yCoord, zCoord, Library.NEG_X),
			//new DirPos(xCoord, yCoord, zCoord + 1, Library.POS_Z),
			//new DirPos(xCoord, yCoord, zCoord - 1, Library.NEG_Z)
		};
	}

	private void updateConnections() {
		for(DirPos pos : getConPos()) {
			this.trySubscribe(worldObj, pos.getX(), pos.getY(), pos.getZ(), pos.getDir());
		}
	}

	public void consumePower(long amount) {
		this.setStoredEnergyQuanta(Math.max(0, energyQuanta - amount));
	}
	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		energyQuanta = EnergyUnits.readEnergyQuanta(nbt, "power");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		EnergyUnits.writeEnergyQuanta(nbt, energyQuanta);
	}

	@Override
	public boolean isLoaded() {
		return true;
	}

	@Override
	public long getStoredEnergyQuanta() {
		return energyQuanta;
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	public boolean hasPower() {
		return energyQuanta >= consumption;
	}

	public boolean hasPowerForShot() {
		return energyQuanta >= POWER_PER_SHOT;
	}

	public void consumeShotPower() {
		consumePower(POWER_PER_SHOT);
	}

	@Override
	public void setStoredEnergyQuanta(long i) {
		if(this.energyQuanta == i) return;
		this.energyQuanta = i;
		this.markPowerNetDirty();
	}
}
