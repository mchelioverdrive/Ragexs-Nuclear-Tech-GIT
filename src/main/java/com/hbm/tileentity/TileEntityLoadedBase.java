package com.hbm.tileentity;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.BufPacket;
import com.hbm.sound.AudioWrapper;

import api.hbm.tile.ILoadedTile;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityLoadedBase extends TileEntity implements ILoadedTile {

	public boolean isLoaded = true;
	public boolean muffled = false;
	private ByteBuf lastPackedBuf = null;

	@Override
	public boolean isLoaded() {
		return isLoaded;
	}

	@Override
	public void onChunkUnload() {
		super.onChunkUnload();
		this.isLoaded = false;
		releasePackedBuf();
	}

	@Override
	public void invalidate() {
		super.invalidate();
		releasePackedBuf();
	}

	private void releasePackedBuf() {
		if(this.lastPackedBuf != null) {
			this.lastPackedBuf.release();
			this.lastPackedBuf = null;
		}
	}

	public AudioWrapper createAudioLoop() { return null; }

	public AudioWrapper rebootAudio(AudioWrapper wrapper) {
		wrapper.stopSound();
		AudioWrapper audio = createAudioLoop();
		audio.startSound();
		return audio;
	}

	/** Sends a precompiled ByteBuf sync packet and suppresses unchanged repeats between periodic refreshes. */
	public void networkPackNT(int range) {
		if(worldObj.isRemote) {
			return;
		}

		ByteBuf buf = BufPacket.compile(xCoord, yCoord, zCoord, (IBufPacketReceiver) this);

		if(lastPackedBuf != null && buf.equals(lastPackedBuf) && worldObj.getWorldTime() % 20 != 0) {
			buf.release();
			return;
		}

		ByteBuf old = this.lastPackedBuf;
		this.lastPackedBuf = buf.copy();
		if(old != null) {
			old.release();
		}

		try {
			PacketDispatcher.wrapper.sendToAllAround(new BufPacket(buf), new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, range));
		} finally {
			buf.release();
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);
		this.muffled = nbt.getBoolean("muffled");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("muffled", muffled);
	}

	public float getVolume(float baseVolume) {
		return muffled ? baseVolume * 0.1F : baseVolume;
	}
}
