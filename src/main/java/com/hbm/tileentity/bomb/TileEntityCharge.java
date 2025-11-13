package com.hbm.tileentity.bomb;

import com.hbm.blocks.bomb.BlockChargeBase;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.NBTPacket;
import com.hbm.tileentity.INBTPacketReceiver;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityCharge extends TileEntity implements INBTPacketReceiver {

	//DISARM???
	public boolean defusePending = false;
	public int defusePendingTicks = 0;
	public static final int DEFUSE_DELAY_TICKS = 100; // 5 seconds (100 ticks)

	public boolean started;
	public int timer;

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void updateEntity() {

		if(!worldObj.isRemote) {

			// --- Minimal pending defuse processing (runs server-side) ---
			if(this.defusePending) {
				this.defusePendingTicks--;

				// if timer still positive, play a tick sound occasionally (optional)
				if(this.defusePendingTicks > 0 && this.defusePendingTicks % 20 == 0)
					worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "hbm:item.defuseTick", 0.8F, 1.0F);

				if(this.defusePendingTicks <= 0) {
					// Complete the deferred disarm exactly like original code
					this.defusePending = false;
					this.defusePendingTicks = 0;

					// Ensure the bomb won't explode when block removal triggers breakBlock
					BlockChargeBase.safe = true;
					// call the exact dismantle behaviour (this triggers breakBlock -> no explode due to safe)
					((BlockChargeBase)this.getBlockType()).dismantle(worldObj, xCoord, yCoord, zCoord);
					BlockChargeBase.safe = false;

					// mark and update
					this.markDirty();
					worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				} else {
					// keep tile dirty so network update will carry countdown if you rely on it
					this.markDirty();
					if(this.defusePendingTicks % 10 == 0)
						worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
				}
			}

			if(started) {
				timer--;

				if(timer % 20 == 0 && timer > 0)
					worldObj.playSoundEffect(xCoord, yCoord, zCoord, "hbm:weapon.fstbmbPing", 1.0F, 1.0F);

				if(timer <= 0) {
					((BlockChargeBase)this.getBlockType()).explode(worldObj, xCoord, yCoord, zCoord);
				}
			}

			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("timer", timer);
			data.setBoolean("started", started);
			data.setBoolean("defusePending", defusePending);
			data.setInteger("defusePendingTicks", defusePendingTicks);
			PacketDispatcher.wrapper.sendToAllAround(new NBTPacket(data, xCoord, yCoord, zCoord), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 100));
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
		timer = data.getInteger("timer");
		started = data.getBoolean("started");
		defusePending = data.getBoolean("defusePending");
		defusePendingTicks = data.getInteger("defusePendingTicks");
	}

	public String getMinutes() {

		String mins = "" + (timer / 1200);

		if(mins.length() == 1)
			mins = "0" + mins;

		return mins;
	}

	public String getSeconds() {

		String mins = "" + ((timer / 20) % 60);

		if(mins.length() == 1)
			mins = "0" + mins;

		return mins;
	}
}
