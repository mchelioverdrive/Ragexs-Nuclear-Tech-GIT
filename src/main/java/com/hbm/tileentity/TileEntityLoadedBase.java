package com.hbm.tileentity;

import com.hbm.sound.AudioWrapper;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.machine.MachineKey;
import com.hbm.machine.MachineRuntimeManager;

import api.hbm.energymk2.IEnergyHandlerMK2;
import api.hbm.energymk2.PowerNetMK2;
import api.hbm.fluid.IFluidConnector;
import api.hbm.fluidmk2.FluidNetEndpointRegistry;
import api.hbm.fluidmk2.IFluidProviderMK2;
import api.hbm.tile.ILoadedTile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityLoadedBase extends TileEntity implements ILoadedTile {
	
	public boolean isLoaded = true;
	public boolean muffled = false;
	private long machineLifecycleGeneration;
	private transient MachineKey machineRuntimeBinding;
	private transient boolean retainMachineRuntimeOnInvalidate;
	
	@Override
	public boolean isLoaded() {
		return isLoaded;
	}

	@Override
	public void validate() {
		super.validate();
		this.isLoaded = true;
		if(this.worldObj != null && !this.worldObj.isRemote) MachineRuntimeManager.bind(this);
	}

	@Override
	public void onChunkUnload() {
		if(this.worldObj != null && !this.worldObj.isRemote) MachineRuntimeManager.unbind(this);
		if(this.worldObj != null && !this.worldObj.isRemote && this instanceof IEnergyHandlerMK2) PowerNetMK2.detachEndpoint((IEnergyHandlerMK2) this);
		if(this.worldObj != null && !this.worldObj.isRemote && (this instanceof IFluidConnector || this instanceof IFluidProviderMK2)) FluidNetEndpointRegistry.detach(this);
		super.onChunkUnload();
		this.isLoaded = false;
	}

	@Override
	public void invalidate() {
		if(this.worldObj != null && !this.worldObj.isRemote) {
			if(this.retainMachineRuntimeOnInvalidate) MachineRuntimeManager.unbind(this);
			else MachineRuntimeManager.remove(this);
		}
		if(this.worldObj != null && !this.worldObj.isRemote && this instanceof IEnergyHandlerMK2) PowerNetMK2.detachEndpoint((IEnergyHandlerMK2) this);
		if(this.worldObj != null && !this.worldObj.isRemote && (this instanceof IFluidConnector || this instanceof IFluidProviderMK2)) FluidNetEndpointRegistry.detach(this);
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
		this.machineLifecycleGeneration = nbt.getLong("hbmMachineGeneration");
	}
	
	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		nbt.setBoolean("muffled", muffled);
		if(this.machineLifecycleGeneration > 0L) nbt.setLong("hbmMachineGeneration", this.machineLifecycleGeneration);
	}

	/** Override with composable MachineExecutionStrategy flags to opt into the runtime. */
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.LEGACY;
	}

	/** Stable compatibility identifier; opted-in machines may override this across class renames. */
	public String getMachineRuntimeType() {
		return this.getClass().getName();
	}

	/** Called once with all causes coalesced for this scheduler pass. */
	public void onMachineRuntimeDirty(int causes) { }

	/** Called for a due typed transition. taskSlot permits independent machine lanes. */
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) { }

	/** Called only for explicitly selected shared cadence buckets. */
	public void onMachineCoarsePoll(int cadence) { }

	public final void markMachineDirty(int causes) {
		MachineRuntimeManager.markDirty(this, causes);
	}

	public final void markMachineEnergyDirty() {
		this.markMachineDirty(MachineDirtyCause.ENERGY);
	}

	public final void markMachineFluidDirty() {
		this.markMachineDirty(MachineDirtyCause.FLUID | MachineDirtyCause.RECIPE);
	}

	public final boolean scheduleMachineTransition(long dueTick, int taskType, int taskSlot) {
		return MachineRuntimeManager.schedule(this, dueTick, taskType, taskSlot);
	}

	public final boolean cancelMachineTransition(int taskType, int taskSlot) {
		return MachineRuntimeManager.cancel(this, taskType, taskSlot);
	}

	/** Keeps logical identity across a deliberate same-TileEntity block-state swap. */
	public final void beginRetainedMachineBlockTransition() {
		this.retainMachineRuntimeOnInvalidate = true;
		MachineRuntimeManager.beginRetainedTransition(this);
	}

	public final void endRetainedMachineBlockTransition() {
		MachineRuntimeManager.endRetainedTransition(this);
		this.retainMachineRuntimeOnInvalidate = false;
	}

	public final boolean isRetainingMachineRuntimeOnInvalidate() {
		return this.retainMachineRuntimeOnInvalidate;
	}

	public final long getMachineLifecycleGeneration() {
		return machineLifecycleGeneration;
	}

	public final void adoptMachineLifecycleGeneration(long generation) {
		if(generation <= 0L || this.machineLifecycleGeneration == generation) return;
		this.machineLifecycleGeneration = generation;
		this.markDirty();
	}

	public final MachineKey getMachineRuntimeBinding() {
		return machineRuntimeBinding;
	}

	public final void setMachineRuntimeBinding(MachineKey key) {
		this.machineRuntimeBinding = key;
	}

	public final void clearMachineRuntimeBinding() {
		this.machineRuntimeBinding = null;
	}
	
	public float getVolume(float baseVolume) {
		return muffled ? baseVolume * 0.1F : baseVolume;
	}
}
