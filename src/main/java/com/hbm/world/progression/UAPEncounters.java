package com.hbm.world.progression;

import java.util.ArrayList;
import java.util.List;

import com.hbm.config.SpaceConfig;
import com.hbm.config.UAPConfig;
import com.hbm.dim.moon.WorldProviderMoon;
import com.hbm.entity.mob.EntityUAP;
import com.hbm.world.gemini.GeminiHostileSpawning;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;

/** The Overworld owns both milestones and the natural encounter schedule for the save. */
public final class UAPEncounters extends WorldSavedData {

	private static final String NAME = "rntUAPEncounters";
	private static final int ATTEMPT_INTERVAL = 600;
	private static final double OBSERVATION_RANGE = 64D;
	private static final double ENCOUNTER_SEPARATION = 2 * OBSERVATION_RANGE;

	private boolean moonVisited;
	private boolean nuclearDetonation;
	// Total world time is saved, advances only while the world ticks, and ignores /time set.
	private long initialReadyTick = -1;
	private long lastEncounterTick = -1;

	public UAPEncounters(String name) {
		super(name);
	}

	private static UAPEncounters get(World overworld) {
		UAPEncounters data = (UAPEncounters) overworld.mapStorage.loadData(UAPEncounters.class, NAME);
		if(data == null) {
			data = new UAPEncounters(NAME);
			overworld.mapStorage.setData(NAME, data);
		}
		return data;
	}

	public static void recordArrival(EntityPlayer player) {
		World world = player.worldObj;
		if(!(player instanceof EntityPlayerMP) || player instanceof FakePlayer || world.isRemote
				|| !(world.provider instanceof WorldProviderMoon) || player.dimension != world.provider.dimensionId
				|| player.dimension != SpaceConfig.moonDimension) return;
		World overworld = DimensionManager.getWorld(0);
		if(overworld == null) return;
		UAPEncounters data = get(overworld);
		if(data.moonVisited) return;
		data.moonVisited = true;
		data.markDirty();
		data.startDelay(overworld.getTotalWorldTime(), UAPConfig.settings);
	}

	public static void recordNuclearDetonation(World world) {
		if(world.isRemote || world.provider.dimensionId != 0) return;
		UAPEncounters data = get(world);
		if(data.nuclearDetonation) return;
		data.nuclearDetonation = true;
		data.markDirty();
		data.startDelay(world.getTotalWorldTime(), UAPConfig.settings);
	}

	private boolean requirementsMet(UAPConfig.Values settings) {
		return (!settings.requireMoonExploration || moonVisited)
				&& (!settings.requireNuclearDetonation || nuclearDetonation);
	}

	private void startDelay(long now, UAPConfig.Values settings) {
		if(initialReadyTick >= 0 || !requirementsMet(settings)) return;
		initialReadyTick = now + settings.initialDelayTicks;
		markDirty();
	}

	/** Called by the existing server world-tick handler. Placement is attempted at most once per interval. */
	public static void tick(World world) {
		if(world.isRemote || world.provider.dimensionId != 0) return;
		long now = world.getTotalWorldTime();
		if(now % 20 != 0) return;
		UAPConfig.Values settings = UAPConfig.settings;
		UAPEncounters data = get(world);
		data.startDelay(now, settings);
		if(now % ATTEMPT_INTERVAL != 0 || !settings.enabled || settings.maxActivePerDimension == 0
				|| !data.requirementsMet(settings) || now < data.initialReadyTick
				|| (data.lastEncounterTick >= 0 && now - data.lastEncounterTick < settings.minimumCooldownTicks)
				|| world.playerEntities.isEmpty() || world.difficultySetting == EnumDifficulty.PEACEFUL
				|| !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) return;

		long timeOfDay = world.getWorldTime() % 24000L;
		boolean night = timeOfDay >= 13000L && timeOfDay < 23000L;
		if(world.rand.nextInt(settings.preferNighttime && !night ? 96 : 12) != 0) return;

		EntityPlayer player = (EntityPlayer) world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
		if(!(player instanceof EntityPlayerMP) || player instanceof FakePlayer || !player.isEntityAlive()
				|| player.isPlayerSleeping()) return;
		List<EntityUAP> active = new ArrayList<>();
		for(Object entity : world.loadedEntityList) {
			if(entity instanceof EntityUAP && !((EntityUAP) entity).isDead) {
				active.add((EntityUAP) entity);
				if(active.size() >= settings.maxActivePerDimension) return;
			}
		}
		if(spawnNear(world, (EntityPlayerMP) player, active)) {
			data.lastEncounterTick = now;
			data.markDirty();
		}
	}

	private static boolean spawnNear(World world, EntityPlayerMP player, List<EntityUAP> active) {
		int maxRadius = Math.min(40, player.mcServer.getConfigurationManager().getEntityViewDistance() - 1);
		if(maxRadius < 24) return false;
		for(int attempt = 0; attempt < 4; attempt++) {
			double angle = world.rand.nextDouble() * Math.PI * 2D;
			int radius = 24 + world.rand.nextInt(maxRadius - 23);
			int x = MathHelper.floor_double(player.posX + Math.cos(angle) * radius);
			int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * radius);
			if(!world.blockExists(x, 0, z)) continue;
			if(!farFromActiveEncounters(x + 0.5D, z + 0.5D, active)) continue;
			int y = Math.max(world.getHeightValue(x, z) + 18 + world.rand.nextInt(17), MathHelper.floor_double(player.posY) + 12);
			if(y + 6 >= world.getActualHeight() || player.getDistanceSq(x + 0.5D, y + 1D, z + 0.5D)
					> OBSERVATION_RANGE * OBSERVATION_RANGE) continue;

			EntityUAP uap = new EntityUAP(world, true);
			uap.setLocationAndAngles(x + 0.5D, y, z + 0.5D, world.rand.nextFloat() * 360F, 0F);
			AxisAlignedBB box = uap.boundingBox;
			int px = MathHelper.floor_double(player.posX), pz = MathHelper.floor_double(player.posZ);
			// Cover the collision envelope and the entire sight line before any block reads.
			if(!world.checkChunksExist(Math.min(px, MathHelper.floor_double(box.minX)), y,
					Math.min(pz, MathHelper.floor_double(box.minZ)), Math.max(px, MathHelper.floor_double(box.maxX)),
					(int) box.maxY, Math.max(pz, MathHelper.floor_double(box.maxZ)))) continue;
			if(!world.canBlockSeeTheSky(x, y, z) || !world.getCollidingBoundingBoxes(uap, box).isEmpty()
					|| world.isAnyLiquid(box) || !world.checkNoEntityCollision(box)) continue;
			if(world.rayTraceBlocks(Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ),
					Vec3.createVectorHelper(uap.posX, uap.posY + 1D, uap.posZ)) != null) continue;
			if(!GeminiHostileSpawning.allowAutomaticHostile(uap)) return false;
			return world.spawnEntityInWorld(uap);
		}
		return false;
	}

	private static boolean farFromActiveEncounters(double x, double z, List<EntityUAP> active) {
		for(EntityUAP uap : active) {
			double dx = uap.posX - x;
			double dz = uap.posZ - z;
			if(dx * dx + dz * dz <= ENCOUNTER_SEPARATION * ENCOUNTER_SEPARATION) return false;
		}
		return true;
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		moonVisited = tag.getBoolean("MoonVisited");
		nuclearDetonation = tag.getBoolean("NuclearDetonation");
		initialReadyTick = tag.hasKey("InitialReadyTick") ? tag.getLong("InitialReadyTick") : -1;
		lastEncounterTick = tag.hasKey("LastEncounterTick") ? tag.getLong("LastEncounterTick") : -1;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		tag.setBoolean("MoonVisited", moonVisited);
		tag.setBoolean("NuclearDetonation", nuclearDetonation);
		tag.setLong("InitialReadyTick", initialReadyTick);
		tag.setLong("LastEncounterTick", lastEncounterTick);
	}
}
