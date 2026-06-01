package com.hbm.dim.orbit;

import com.hbm.config.SpaceConfig;
import com.hbm.dim.CelestialBody;
import com.hbm.dim.SolarSystem;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.dim.trait.CBT_Atmosphere;
import com.hbm.dim.trait.CelestialBodyTrait.CBT_Destroyed;
import com.hbm.handler.atmosphere.ChunkAtmosphereManager;
import com.hbm.lib.Library;
import com.hbm.potion.HbmPotion;
import com.hbm.util.AstronomyUtil;
import com.hbm.util.BobMathUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.util.WeightedRandomFishable;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldProviderOrbit extends WorldProvider {

	// How fast orbital simulation runs relative to real seconds
	//unused
	//public static final double ORBIT_SCALE = 1.0 / 100000.0;

	//todo spawn our FRIEND here when there's an atmosphere, it's breathable, and there's a door. then our friend can KNOCK.

	// Orbit at an altitude that provides an hour-long realtime orbit (game time is fast so we go slow)
	// We want a consistent orbital period to prevent orbiting too slow or fast (both for player comfort and feel)
	//private static final double ORBIT_PERIOD_SECONDS = AstronomyUtil.SECONDS_IN_MC_DAY * 5; // 5 MC days per orbit
	private static final double ORBIT_PERIOD_SECONDS = 60 * 60 * 2; // 2 real hours per orbit

	protected float getOrbitalAltitude(CelestialBody body) {

		if(body.parent == null) {
			// stars
			return (float)(body.radiusKm * 0.03D);
		}

		// planets/moons
		return (float)(body.radiusKm * 0.08D);
	}

	// r = ∛[(G x Me x T2) / (4π2)]
	private double getAltitudeForPeriod(double massKg, double periodSeconds) {

		double G = AstronomyUtil.GRAVITATIONAL_CONSTANT;

		return Math.cbrt(
			(G * massKg * periodSeconds * periodSeconds)
				/ (4.0 * Math.PI * Math.PI)
		);
	}

	public float getSunPower() {

		CelestialBody body = OrbitalStation.clientStation.orbiting;

		double rAU = body.getPlanet().semiMajorAxisKm / AstronomyUtil.KM_IN_AU;

		return (float)(1.0 / (rAU * rAU));
	}

	@Override
	public void registerWorldChunkManager() {
		this.worldChunkMgr = new WorldChunkManagerHell(new BiomeGenOrbit(SpaceConfig.orbitBiome), dimensionId);
	}

	@Override
	public String getDimensionName() {
		return "Orbit";
	}

	@Override
	public IChunkProvider createChunkGenerator() {
		return new ChunkProviderOrbit(this.worldObj);
	}

	private boolean playerHasAtmosphere(EntityPlayer player) {
		//checks if the player has an atmosphere for radiation effects
		CBT_Atmosphere atm = ChunkAtmosphereManager.proxy.getAtmosphere(
			player.worldObj,
			(int) player.posX, (int) player.posY, (int) player.posZ
		);

		if (atm == null || atm.fluids == null) return false;

		for (CBT_Atmosphere.FluidEntry entry : atm.fluids) {
			if (entry.pressure > 0.02) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void updateWeather() {
		super.updateWeather();

		// Apply radiation effect to players in orbit
		if (!worldObj.isRemote) {
			Random rand = new Random();

			for (Object obj : worldObj.playerEntities) {
				if (obj instanceof EntityPlayer) {
					EntityPlayer player = (EntityPlayer) obj;

					//todone when added cryochamber,
					// if not in cryo chamber, or riding rocket (drop pods, etc)
					//we won't need to do all that because if you're dumb enough to put
					// a fucking cryochamber in nil atmosphere you deserve the rads

					//todone more conditions like shielding, atmosphere

					if (playerHasAtmosphere(player)) {
						continue;
					}
					//works

					// Check if the player can see the sky
					if (worldObj.canBlockSeeTheSky((int) player.posX, (int) player.posY, (int) player.posZ)) {
						// Apply radiation effect with a random chance
						if (rand.nextInt(80) == 0) {
							player.addPotionEffect(new PotionEffect(HbmPotion.radiation.id, 100, 1));
						}
					}
				}
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getFogColor(float x, float y) {
		return Vec3.createVectorHelper(0, 0, 0);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getSkyColor(Entity camera, float partialTicks) {
		return Vec3.createVectorHelper(0, 0, 0);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {
		return null;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getStarBrightness(float par1) {
		// Stars look cool in orbit, but obvs at Moho we don't want the big fuckoff sun to not extinguish
		// Stars become visible during the day part of orbit just before Earth
		// And are fully visible during the day beyond the orbit of Duna (mars)
		float distanceStart = 9_000_000;
		float distanceEnd = 30_000_000;

		double progress = OrbitalStation.clientStation.getTransferProgress(par1);
		double semiMajorAxisKm = OrbitalStation.clientStation.orbiting.getPlanet().semiMajorAxisKm;
		if(progress > 0) {
			semiMajorAxisKm = (float)BobMathUtil.lerp(progress, semiMajorAxisKm, OrbitalStation.clientStation.target.getPlanet().semiMajorAxisKm);
		}

		float distanceFactor = MathHelper.clamp_float((float) ((semiMajorAxisKm - distanceStart) / (distanceEnd - distanceStart)), 0F, 1F);

		//float celestialAngle = worldObj.getCelestialAngle(par1);
		//float celestialPhase = (1 - (celestialAngle + 0.5F) % 1) * 2 - 1;
		//float starBrightness = (float)Library.smoothstep(Math.abs(celestialPhase), 0.6, 0.75);
		float angle = worldObj.getCelestialAngle(par1);

		// convert to night factor
		float night = Math.abs(angle - 0.5F) * 2.0F;
		night = MathHelper.clamp_float(night, 0F, 1F);

		// smooth curve
		float starBrightness = night * night;

		return MathHelper.clamp_float(starBrightness, distanceFactor, 1F);
	}

	@Override
	public float getSunBrightness(float partialTicks) {
		//this is currently bugged and everytime I tried to fix it to adjust brightness to whether or not the sun was visible
		//it would either
		//A. make everything bright regardless of if the sun is visible
		//or
		//B. make everything dark regardless of if the sun is visible
		//so for now I'm leaving it like this where it's only slightly dark, however distance from the sun still affects brightness... I think.
		//or at least it did? Nevermind. It now does not. But it SHOULD.
		//basically from my shitty understanding this affects the brightness of the orbital station...
		//todo: fix crap

		CelestialBody orbiting = OrbitalStation.clientStation.orbiting;
		float solarPower = getSunPower();

		if (orbiting.parent == null) {
			return MathHelper.clamp_float(solarPower, 0F, 1F);
		}

		double ticks =
			SolarSystem.getCelestialTicks(worldObj, partialTicks)
				* AstronomyUtil.TIME_MULTIPLIER;

		// observer position in system space
		Vec3 observer =
			SolarSystem.calculatePosition(orbiting, 0, ticks);

		// SUN IS ALWAYS ORIGIN IN YOUR MODEL
		Vec3 toSun = Vec3.createVectorHelper(
			-observer.xCoord,
			-observer.yCoord,
			-observer.zCoord
		);

		double dist = toSun.lengthVector();

		if (dist < 1e-6) {
			return solarPower;
		}

		toSun = toSun.normalize();

		// IMPORTANT: use orbital plane normal (your system is 2D XY)
		Vec3 normal = Vec3.createVectorHelper(0, 0, 1);

		double dot = Math.max(0.0, toSun.dotProduct(normal));

		// optional: soften curve so it doesn't clamp instantly
		float brightness = (float)(solarPower * Math.pow(dot, 1.2));

		return MathHelper.clamp_float(brightness, 0F, 1F);
	}

	@Override
	public boolean canDoLightning(Chunk chunk) {
		return false;
	}

	@Override
	public boolean canDoRainSnowIce(Chunk chunk) {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getCloudHeight() {
		return -99999;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IRenderHandler getSkyRenderer() {
		return new SkyProviderOrbit();
	}

	@Override
	public float calculateCelestialAngle(long worldTime, float partialTicks) {
		CelestialBody orbiting = OrbitalStation.clientStation.orbiting;
		CelestialBody target = OrbitalStation.clientStation.target;
		double progress = OrbitalStation.clientStation.getTransferProgress(partialTicks);
		float angle = (float)SolarSystem.calculateSingleAngle(worldObj, partialTicks, orbiting, getOrbitalAltitude(orbiting));
		if(progress > 0) {
			angle = (float)BobMathUtil.lerp(progress, angle, (float)SolarSystem.calculateSingleAngle(worldObj, partialTicks, target, getOrbitalAltitude(target)));
		}
		return (float)(angle / 360.0);
	}

	// Same shit as in Celestial
	@Override
	public int getRespawnDimension(EntityPlayerMP player) {
		ChunkCoordinates coords = player.getBedLocation(dimensionId);

		// If no bed, respawn in overworld
		if(coords == null)
			return 0;

		// If the bed location has no breathable atmosphere, respawn in overworld
		CBT_Atmosphere atmosphere = ChunkAtmosphereManager.proxy.getAtmosphere(worldObj, coords.posX, coords.posY, coords.posZ);
		if(!ChunkAtmosphereManager.proxy.canBreathe(atmosphere))
			return 0;

		return dimensionId;
	}

	@Override
	public boolean canRespawnHere() {
		if(WorldProviderCelestial.attemptingSleep) {
			WorldProviderCelestial.attemptingSleep = false;
			return true;
		}

		return false;
	}

	private static ArrayList<WeightedRandomFishable> plushie;

	private ArrayList<WeightedRandomFishable> getPlushie() {
		if(plushie == null) {
			plushie = new ArrayList<>();
			plushie.add(new WeightedRandomFishable(new ItemStack(Blocks.air, 1, 1), 100));
			//DIE
		}
//
		return plushie;
	}

	/// FISH ///
	public ArrayList<WeightedRandomFishable> getFish() {
		return getPlushie();
	}
	//
	public ArrayList<WeightedRandomFishable> getJunk() {
		return getPlushie();
	}
	//
	public ArrayList<WeightedRandomFishable> getTreasure() {
		return getPlushie();
	}

}
