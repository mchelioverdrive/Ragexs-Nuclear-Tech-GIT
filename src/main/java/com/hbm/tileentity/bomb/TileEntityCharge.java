package com.hbm.tileentity.bomb;

import com.hbm.blocks.bomb.BlockChargeBase;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.NBTPacket;
import com.hbm.tileentity.INBTPacketReceiver;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityCharge extends TileEntity implements INBTPacketReceiver {

	public boolean started;
	public int timer;

	// Defuse state
	public boolean defusing;
	public int defuseTicks;
	public String defuserName = "";

	// Configurable values
	public static final int DEFUSE_TIME_TICKS = 100; // 100 ticks = 5 seconds
	public static final double MAX_DEFUSE_DISTANCE = 3.0D; // blocks

	@Override
	public void updateEntity() {
		if (worldObj == null)
			return;

		if (!worldObj.isRemote) {

			// --- Countdown / explosion logic ---
			if (started) {
				timer--;

				if (timer % 20 == 0 && timer > 0)
					worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "hbm:weapon.fstbmbPing", 1.0F, 1.0F);

				if (timer <= 0) {
					// prevent defuse continuing after explosion
					defusing = false;
					defuseTicks = 0;
					defuserName = "";

					((BlockChargeBase) this.getBlockType()).explode(worldObj, xCoord, yCoord, zCoord);
					// once exploded, nothing else to do this tick
				}
			}

			// --- Defuse progression on server ---
			if (defusing) {
				EntityPlayer p = worldObj.getPlayerEntityByName(defuserName);
				boolean cancel = false;

				if (p == null || p.isDead) {
					cancel = true;
				} else {
					double dx = p.posX - (xCoord + 0.5D);
					double dy = p.posY - (yCoord + 0.5D);
					double dz = p.posZ - (zCoord + 0.5D);
					double distSq = dx * dx + dy * dy + dz * dz;
					if (distSq > MAX_DEFUSE_DISTANCE * MAX_DEFUSE_DISTANCE)
						cancel = true;

					// Require the player to keep sneaking to defuse
					if (!p.isSneaking())
						cancel = true;

					// OPTIONAL: require the player to hold the defuser item here
					// if(p.getHeldItem() == null || !(p.getHeldItem().getItem() instanceof ItemDefuser)) cancel = true;
				}

				if (cancel) {
					defusing = false;
					defuseTicks = 0;
					defuserName = "";
					markDirty();
					worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
					worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "hbm:item.defuseStop", 1.0F, 1.0F);
				} else {
					// Advance defuse progress
					defuseTicks++;

					// feedback ticks (every second)
					if (defuseTicks % 20 == 0) {
						worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "hbm:item.defuseTick", 0.8F, 1.0F);
					}

					// Defuse complete
					if (defuseTicks >= DEFUSE_TIME_TICKS) {
						// mark disarmed
						defusing = false;
						defuseTicks = 0;
						String who = defuserName;
						defuserName = "";
						started = false;
						timer = 0;
						markDirty();
						worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

						// play completion sound
						worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "hbm:item.defuseComplete", 1.0F, 1.0F);

						// Call block dismantle using the safe flag on BlockChargeBase to avoid explode
						BlockChargeBase.safe = true;
						BlockChargeBase block = (BlockChargeBase) worldObj.getBlock(xCoord, yCoord, zCoord);
						if (block != null) {
							block.dismantle(worldObj, xCoord, yCoord, zCoord);
						} else {
							worldObj.setBlockToAir(xCoord, yCoord, zCoord);
						}
						BlockChargeBase.safe = false;

						// notify player who defused (if still online)
						EntityPlayer p2 = worldObj.getPlayerEntityByName(who);
						if (p2 != null) {
							p2.addChatMessage(new net.minecraft.util.ChatComponentText("\u00a7aBomb disarmed."));
						}
					} else {
						markDirty();
						// also mark for update every so often so clients update progress
						if (defuseTicks % 10 == 0)
							worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
					}
				}
			}

			// Sync state to clients
			NBTTagCompound data = new NBTTagCompound();
			data.setInteger("timer", timer);
			data.setBoolean("started", started);
			data.setBoolean("defusing", defusing);
			data.setInteger("defuseTicks", defuseTicks);
			data.setString("defuserName", defuserName == null ? "" : defuserName);

			PacketDispatcher.wrapper.sendToAllAround(
				new NBTPacket(data, xCoord, yCoord, zCoord),
				new TargetPoint(this.worldObj.provider.dimensionId, xCoord, yCoord, zCoord, 100));
		}
	}

	@Override
	public void networkUnpack(NBTTagCompound data) {
		if (data == null) return;
		timer = data.getInteger("timer");
		started = data.getBoolean("started");

		// client-side defuse info (for HUD/progress)
		defusing = data.getBoolean("defusing");
		defuseTicks = data.getInteger("defuseTicks");
		defuserName = data.getString("defuserName");
	}

	public String getMinutes() {
		String mins = "" + (timer / 1200);

		if (mins.length() == 1)
			mins = "0" + mins;

		return mins;
	}

	public String getSeconds() {
		String secs = "" + ((timer / 20) % 60);

		if (secs.length() == 1)
			secs = "0" + secs;

		return secs;
	}

	/**
	 * Helper for client UI: returns defuse progress 0.0 - 1.0 (useful for progress bar)
	 */
	public float getDefuseProgress() {
		if (!defusing || DEFUSE_TIME_TICKS <= 0) return 0.0F;
		return Math.min(1.0F, (float) defuseTicks / (float) DEFUSE_TIME_TICKS);
	}
}
