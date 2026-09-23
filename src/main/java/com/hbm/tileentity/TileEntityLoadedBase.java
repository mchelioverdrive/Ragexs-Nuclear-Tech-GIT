package com.hbm.tileentity;

import com.hbm.sound.AudioWrapper;

import api.hbm.energymk2.IEnergyHandlerMK2;
import api.hbm.energymk2.PowerNetMK2;
import api.hbm.tile.ILoadedTile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityLoadedBase extends TileEntity implements ILoadedTile {
	
	public boolean isLoaded = true;
	public boolean muffled = false;
	
	@Override
	public boolean isLoaded() {
		return isLoaded;
	}

	@Override
	public void onChunkUnload() {
		if(this.worldObj != null && !this.worldObj.isRemote && this instanceof IEnergyHandlerMK2) PowerNetMK2.detachEndpoint((IEnergyHandlerMK2) this);
		super.onChunkUnload();
		this.isLoaded = false;
	}

	@Override
	public void invalidate() {
		if(this.worldObj != null && !this.worldObj.isRemote && this instanceof IEnergyHandlerMK2) PowerNetMK2.detachEndpoint((IEnergyHandlerMK2) this);
		super.invalidate();
	}
	
	public AudioWrapper createAudioLoop() { return null; }
	
	public AudioWrapper rebootAudio(AudioWrapper wrapper) {
		wrapper.stopSound();
		AudioWrapper audio = createAudioLoop();
		audio.startSound();
		return audio;
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
