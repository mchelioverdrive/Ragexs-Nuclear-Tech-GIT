package com.hbm.tileentity.machine;

import com.hbm.interfaces.IControlReceiver;
import com.hbm.machine.MachineDirtyCause;
import com.hbm.machine.MachineExecutionStrategy;
import com.hbm.tileentity.INBTPacketReceiver;
import com.hbm.tileentity.TileEntityLoadedBase;
import com.hbm.tileentity.network.RTTYSystem;
import com.hbm.tileentity.network.RTTYSystem.RTTYListener;
import com.hbm.tileentity.network.RTTYSystem.RTTYChannel;
import com.hbm.util.NoteBuilder;
import com.hbm.util.NoteBuilder.Instrument;
import com.hbm.util.NoteBuilder.Note;
import com.hbm.util.NoteBuilder.Octave;
import com.hbm.util.Tuple.Triplet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

public class TileEntityRadioRec extends TileEntityLoadedBase implements INBTPacketReceiver, IControlReceiver, RTTYListener {
	private static final int TASK_LISTEN = 0;
	private String lastSyncChannel;
	private boolean lastSyncOn;
	private boolean syncInitialized;
	private long lastSyncTick = Long.MIN_VALUE;
	private String subscribedChannel;

	public String channel = "";
	public boolean isOn = false;

	@Override
	public int getMachineExecutionStrategies() {
		return MachineExecutionStrategy.EVENT_DRIVEN | MachineExecutionStrategy.SCHEDULED | MachineExecutionStrategy.COARSE_20;
	}

	@Override
	public void onMachineRuntimeDirty(int causes) {
		if(worldObj == null || worldObj.isRemote) return;
		boolean wasSubscribed = subscribedChannel != null;
		if(subscribedChannel != null && (!isListening() || !subscribedChannel.equals(channel))) {
			RTTYSystem.unsubscribe(worldObj, subscribedChannel, this);
			subscribedChannel = null;
			cancelMachineTransition(TASK_LISTEN, 0);
		}
		if(isListening() && subscribedChannel == null) {
			RTTYSystem.subscribe(worldObj, channel, this);
			subscribedChannel = channel;
			// A tuned receiver already had a task due this tick under the old
			// cadence; retuning must still observe the newly selected channel.
			RTTYChannel current = RTTYSystem.listen(worldObj, channel);
			if(wasSubscribed && current != null && current.timeStamp == worldObj.getTotalWorldTime() - 1L)
				scheduleMachineTransition(worldObj.getTotalWorldTime(), TASK_LISTEN, 0);
		}
		if((causes & (MachineDirtyCause.LIFECYCLE | MachineDirtyCause.CONFIGURATION)) != 0) syncState(true);
	}

	@Override
	public void onRTTYSignal(World world, String channelName) {
		if(world != worldObj || isInvalid() || !isLoaded() || !isListening() || !channel.equals(channelName)) return;
		// The server PRE phase publishes the previous tick's signal; the world
		// runtime observes it after this world's next simulation tick.
		scheduleMachineTransition(world.getTotalWorldTime() + 1L, TASK_LISTEN, 0);
	}

	@Override
	public void onChunkUnload() {
		this.unsubscribeSignal();
		super.onChunkUnload();
	}

	@Override
	public void invalidate() {
		this.unsubscribeSignal();
		super.invalidate();
	}

	private void unsubscribeSignal() {
		if(subscribedChannel == null || worldObj == null || worldObj.isRemote) return;
		RTTYSystem.unsubscribe(worldObj, subscribedChannel, this);
		subscribedChannel = null;
	}

	@Override
	public void onMachineScheduledTransition(int taskType, int taskSlot, long dueTick) {
		if(taskType != TASK_LISTEN || taskSlot != 0 || worldObj == null || worldObj.isRemote || !isListening()) return;
		RTTYChannel chan = RTTYSystem.listen(worldObj, this.channel);

		if(chan != null && chan.timeStamp == worldObj.getTotalWorldTime() - 1) {
			Triplet<Instrument, Note, Octave>[] notes = NoteBuilder.translate(chan.signal + "");

			for(Triplet<Instrument, Note, Octave> note : notes) {
				Instrument i = note.getX();
				Note n = note.getY();
				Octave o = note.getZ();

				int noteId = n.ordinal() + o.ordinal() * 12;
				String s = "harp";

				if(i == Instrument.BASSDRUM) s = "bd";
				if(i == Instrument.SNARE) s = "snare";
				if(i == Instrument.CLICKS)  s = "hat";
				if(i == Instrument.BASSGUITAR)  s = "bassattack";

				float f = (float)Math.pow(2.0D, (double)(noteId - 12) / 12.0D);
				worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "note." + s, 3.0F, f);
			}
		}
		syncState(false);
	}

	@Override
	public void onMachineCoarsePoll(int cadence) {
		if(cadence == 20 && worldObj != null && !worldObj.isRemote) syncState(false);
	}

	private boolean isListening() {
		return isOn && channel != null && !channel.isEmpty();
	}

	private void syncState(boolean force) {
		long now = worldObj.getTotalWorldTime();
		boolean changed = !syncInitialized || lastSyncOn != isOn || (lastSyncChannel == null ? channel != null : !lastSyncChannel.equals(channel));
		if(force || changed || now - lastSyncTick >= 20L) {
			NBTTagCompound data = new NBTTagCompound();
			data.setString("channel", channel);
			data.setBoolean("isOn", isOn);
			INBTPacketReceiver.networkPack(this, data, 15);
			lastSyncChannel = channel;
			lastSyncOn = isOn;
			syncInitialized = true;
			lastSyncTick = now;
		}
	}

	@Override
	public void updateEntity() { }

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		channel = nbt.getString("channel");
		isOn = nbt.getBoolean("isOn");
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		channel = nbt.getString("channel");
		isOn = nbt.getBoolean("isOn");
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		nbt.setString("channel", channel);
		nbt.setBoolean("isOn", isOn);
	}

	@Override
	public boolean hasPermission(EntityPlayer player) {
		return player.getDistance(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 16D;
	}

	@Override
	public void receiveControl(NBTTagCompound data) {
		if(data.hasKey("channel")) this.channel = data.getString("channel");
		if(data.hasKey("isOn")) this.isOn = data.getBoolean("isOn");

		this.markDirty();
		this.markMachineDirty(MachineDirtyCause.CONFIGURATION);
	}
}
