package com.hbm.world.progression;

import com.hbm.config.EuropaLankConfig;
import com.hbm.entity.mob.EntityFRIEND;
import com.hbm.entity.mob.ai.LankTerrain;

import cpw.mods.fml.common.eventhandler.Event.Result;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.ForgeEventFactory;

/** Save-wide excavation and the schedule for the Europa natural spawn pass. */
public final class EuropaLankEncounters extends WorldSavedData {

	private static final String NAME = "rntEuropaLankEncounters";
	private static final int CHECK_INTERVAL = 200;
	private static final int ENCOUNTER_ODDS = 12;

	private long excavated;
	private boolean unlocked;
	private long nextEncounterTick;
	private int activeEncounters;

	public EuropaLankEncounters(String name) {
		super(name);
	}

	private static EuropaLankEncounters get(World overworld) {
		EuropaLankEncounters data = (EuropaLankEncounters) overworld.mapStorage.loadData(EuropaLankEncounters.class, NAME);
		if(data == null) {
			data = new EuropaLankEncounters(NAME);
			overworld.mapStorage.setData(NAME, data);
		}
		return data;
	}

	static void recordExcavation() {
		World overworld = DimensionManager.getWorld(0);
		if(overworld == null) return;
		EuropaLankEncounters data = get(overworld);
		if(data.excavated < Long.MAX_VALUE) data.excavated++;
		data.updateUnlock(EuropaLankConfig.settings);
		data.markDirty();
	}

	private void updateUnlock(EuropaLankConfig.Values settings) {
		if(unlocked || excavated < settings.excavationThreshold) return;
		unlocked = true;
		markDirty();
	}

	public static void tick(World world) {
		if(!EuropaExcavation.isEuropa(world)) return;
		EuropaExcavation.finishBlockEvents(world);
		if(world.getTotalWorldTime() % CHECK_INTERVAL != 0) return;
		World overworld = DimensionManager.getWorld(0);
		if(overworld == null) return;

		EuropaLankConfig.Values settings = EuropaLankConfig.settings;
		EuropaLankEncounters data = get(overworld);
		data.updateUnlock(settings);
		long now = overworld.getTotalWorldTime();
		int active = 0;
		if(data.activeEncounters > 0) {
			for(Object entry : world.loadedEntityList) {
				if(entry instanceof EntityFRIEND && ((EntityFRIEND) entry).isNaturalEncounter()
						&& ((EntityFRIEND) entry).isEntityAlive()) active++;
			}
			if(active < data.activeEncounters) {
				// Natural visitors end on death, hidden disappearance, chunk unload, or restart.
				data.scheduleCooldown(now, settings, active);
				return;
			}
		}
		if(!settings.enabled || settings.requireExcavation && !data.unlocked || now < data.nextEncounterTick
				|| active >= settings.maxActivePerDimension || world.playerEntities.isEmpty()
				|| !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
				|| world.rand.nextInt(ENCOUNTER_ODDS) != 0) return;

		EntityPlayer player = (EntityPlayer) world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
		if(LankTerrain.isTarget(player) && EntityFRIEND.isUnclaimed(player, null) && spawnNear(world, player, settings)) {
			data.scheduleCooldown(now, settings, active + 1);
		}
	}

	private void scheduleCooldown(long now, EuropaLankConfig.Values settings, int active) {
		nextEncounterTick = Math.max(nextEncounterTick, now + settings.minimumCooldownTicks);
		activeEncounters = active;
		markDirty();
	}

	private static boolean spawnNear(World world, EntityPlayer player, EuropaLankConfig.Values settings) {
		EntityFRIEND lank = new EntityFRIEND(world);
		lank.beginNaturalEncounter(player);
		double minSq = settings.minimumStalkingDistance * settings.minimumStalkingDistance;
		double maxSq = settings.maximumStalkingDistance * settings.maximumStalkingDistance;
		int nearY = MathHelper.floor_double(player.boundingBox.minY);
		double angle = world.rand.nextDouble() * Math.PI * 2D;
		ChunkCoordinates selected = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for(int attempt = 0; attempt < 8; attempt++, angle += Math.PI / 4D) {
			double radius = settings.minimumStalkingDistance
					+ (settings.maximumStalkingDistance - settings.minimumStalkingDistance) * world.rand.nextDouble();
			int x = MathHelper.floor_double(player.posX + Math.cos(angle) * radius);
			int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * radius);
			if(!LankTerrain.loadedBetween(world, x, z, x, z, 32)) continue;
			ChunkCoordinates spot = LankTerrain.findFooting(lank, x, nearY, z, 6, true);
			if(spot == null) continue;
			double distance = player.getDistanceSq(x + 0.5D, spot.posY, z + 0.5D);
			if(distance < minSq || distance > maxSq
					|| !LankTerrain.hiddenFromObservers(world, x + 0.5D, spot.posY, z + 0.5D, lank.height, true)) continue;
			lank.setPosition(x + 0.5D, spot.posY, z + 0.5D);
			if(!lank.getCanSpawnHere()) continue;

			// Favor terrain cover and the sides of the player's view instead of directly behind them.
			double midY = spot.posY + lank.height * 0.5D;
			double score = LankTerrain.occluded(player, lank.posX, midY, lank.posZ) ? 2D : 0D;
			score -= Math.abs(LankTerrain.lookDot(player, lank.posX, midY, lank.posZ));
			if(score > bestScore) {
				bestScore = score;
				selected = spot;
			}
		}
		if(selected == null) return false;
		float yaw = (float) (Math.atan2(player.posZ - selected.posZ - 0.5D, player.posX - selected.posX - 0.5D)
				* 180D / Math.PI) - 90F;
		lank.setLocationAndAngles(selected.posX + 0.5D, selected.posY, selected.posZ + 0.5D, yaw, 0F);
		if(ForgeEventFactory.canEntitySpawn(lank, world, (float) lank.posX, (float) lank.posY, (float) lank.posZ) == Result.DENY) return false;
		if(!world.spawnEntityInWorld(lank)) return false;
		if(!ForgeEventFactory.doSpecialSpawn(lank, world, (float) lank.posX, (float) lank.posY, (float) lank.posZ)) lank.onSpawnWithEgg(null);
		return true;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		excavated = Math.max(0L, tag.getLong("Excavated"));
		unlocked = tag.getBoolean("Unlocked");
		nextEncounterTick = Math.max(0L, tag.getLong("NextEncounterTick"));
		activeEncounters = Math.max(0, tag.getInteger("ActiveEncounters"));
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		tag.setLong("Excavated", excavated);
		tag.setBoolean("Unlocked", unlocked);
		tag.setLong("NextEncounterTick", nextEncounterTick);
		tag.setInteger("ActiveEncounters", activeEncounters);
	}
}
