package com.hbm.tileentity.bomb;

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
	private long power;
	private static final long maxPower = 100_000;
	public static final long POWER_PER_SHOT = 250;
	public static final int consumption = 1000;

	//trySubscribe(
	//				worldObj,
	//				xCoord,
	//				yCoord - 1,
	//				zCoord,
	//				net.minecraftforge.common.util.ForgeDirection.UP
	//			);

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {
			updateConnections();

			if(hasPower()) {
				this.power -= consumption;

				if(this.power < 0) {
					this.power = 0;
				}
			}

			if(spin > 0)
				spin -= 1;

			rotation += spin;
			rotation = rotation % 360;
			//I'm pretty sure this is just the barrel rotating, not the actual turret rotation, and it's working just fine, so we need to sync the rotation to the client so it doesn't just sit at 0.-

			PacketDispatcher.wrapper.sendToAll(
				new AuxGaugePacket(
					xCoord,
					yCoord,
					zCoord,
					rotation,
					0
				)
			);
			//when this is on the server, the client will not update the rotation and it will be stuck at 0
			super.updateEntity();
		}
		if (this.hasPower()) {
			super.updateEntity();
		}
		//double up so we're sync'd but ONLY if we have power.-
		//JUST KIDDING IT ACTUALLY STILL SITS AT 0 FOR SOME REASON!!! SO WE NEED TO SYNC SOMETHING

		// Run AI AFTER power has updated
		//this will run on the client:
		//TODO move to server if still bugged

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
		power = Math.max(0, power - amount);
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
	public long getPower() {
		return power;
	}

	@Override
	public long getMaxPower() {
		return maxPower;
	}

	public boolean hasPower() {
		return power > 0;
	}

	@Override
	public void setPower(long i) {
		power = i;
	}
}
