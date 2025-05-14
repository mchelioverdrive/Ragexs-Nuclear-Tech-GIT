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
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.client.IRenderHandler;

import java.util.Random;

public class WorldProviderOrbit extends WorldProvider {

	// Orbit at an altitude that provides an hour-long realtime orbit (game time is fast so we go slow)
	// We want a consistent orbital period to prevent orbiting too slow or fast (both for player comfort and feel)
	private static final float ORBITAL_PERIOD = 7200;

	protected float getOrbitalAltitude(CelestialBody body) {
		return getAltitudeForPeriod(body.massKg, ORBITAL_PERIOD);
	}

	// r = ∛[(G x Me x T2) / (4π2)]
	private float getAltitudeForPeriod(float massKg, float period) {
		return (float)Math.cbrt((AstronomyUtil.GRAVITATIONAL_CONSTANT * massKg * (period * period)) / (4 * Math.PI * Math.PI));
	}

	public float getSunPower() {
		double progress = OrbitalStation.clientStation.getTransferProgress(0);
		float sunPower = OrbitalStation.clientStation.orbiting.getSunPower();
		if(progress > 0) {
			return (float)BobMathUtil.lerp(progress, sunPower, OrbitalStation.clientStation.target.getSunPower());
		}
		return sunPower;
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
			if (entry.pressure > 0.01) {
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
		// And are fully visible during the day beyond the orbit of Duna
		float distanceStart = 9_000_000;
		float distanceEnd = 30_000_000;

		double progress = OrbitalStation.clientStation.getTransferProgress(par1);
		float semiMajorAxisKm = OrbitalStation.clientStation.orbiting.getPlanet().semiMajorAxisKm;
		if(progress > 0) {
			semiMajorAxisKm = (float)BobMathUtil.lerp(progress, semiMajorAxisKm, OrbitalStation.clientStation.target.getPlanet().semiMajorAxisKm);
		}

		float distanceFactor = MathHelper.clamp_float((semiMajorAxisKm - distanceStart) / (distanceEnd - distanceStart), 0F, 1F);

		float celestialAngle = worldObj.getCelestialAngle(par1);
		float celestialPhase = (1 - (celestialAngle + 0.5F) % 1) * 2 - 1;
		float starBrightness = (float)Library.smoothstep(Math.abs(celestialPhase), 0.6, 0.75);

		return MathHelper.clamp_float(starBrightness, distanceFactor, 1F);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getSunBrightness(float par1) {
		if(SolarSystem.kerbol.hasTrait(CBT_Destroyed.class))
			return 0;

		float celestialAngle = worldObj.getCelestialAngle(par1);
		float celestialPhase = (1 - (celestialAngle + 0.5F) % 1) * 2 - 1;

		return 1 - (float)Library.smoothstep(Math.abs(celestialPhase), 0.6, 0.8);
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
		return 0.5F - (angle / 360.0F);
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

}
