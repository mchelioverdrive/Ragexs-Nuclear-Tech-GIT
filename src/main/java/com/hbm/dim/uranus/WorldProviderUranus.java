package com.hbm.dim.uranus;

import com.hbm.dim.WorldChunkManagerCelestial;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.dim.uranus.GenLayerUranus.GenLayerUranusBiomes;
import com.hbm.potion.HbmPotion;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerVoronoiZoom;

public class WorldProviderUranus extends WorldProviderCelestial {

	@Override
	public void registerWorldChunkManager() {

		this.worldChunkMgr =
			new WorldChunkManagerCelestial(
				createUranus(worldObj.getSeed())
			);
	}

	@Override
	public String getDimensionName() {
		return "Uranus";
	}

	@Override
	public IChunkProvider createChunkGenerator() {
		return new ChunkProviderUranus(
			this.worldObj,
			this.getSeed(),
			false
		);
	}

	@Override
	public void updateWeather() {

		// Uranus has no Earth-style rain
		this.worldObj.rainingStrength = 0.0F;
		this.worldObj.thunderingStrength = 0.0F;

		this.worldObj
			.getWorldInfo()
			.setRaining(false);

		this.worldObj
			.getWorldInfo()
			.setThundering(false);

		if(worldObj.isRemote)
			return;

		for(Object obj :
			worldObj.playerEntities) {

			if(!(obj instanceof EntityPlayer))
				continue;

			EntityPlayer player =
				(EntityPlayer)obj;

			int amp = 0;

			// thin upper atmosphere
			// exposed to radiation
			if(player.posY > 220) {

				amp = 8;
			}

			// methane cloud deck
			else if(player.posY > 170) {

				amp = 4;
			}

			// deeper atmosphere
			else if(player.posY > 110) {

				amp = 1;
			}

			// deep atmosphere shielded
			else {

				amp = 0;
			}

			if(amp > 0) {

				player.addPotionEffect(
					new PotionEffect(
						HbmPotion.radiation.id,
						20,
						amp
					)
				);
			}
		}
	}

	private static WorldChunkManagerCelestial.BiomeGenLayers createUranus(long seed) {

		// Uranus is basically one biome:
		// cold methane atmosphere

		GenLayer genlayerBiomes =
			new GenLayerUranusBiomes(seed);

		GenLayerVoronoiZoom genlayerVoronoi =
			new GenLayerVoronoiZoom(
				10L,
				genlayerBiomes
			);

		return new WorldChunkManagerCelestial.BiomeGenLayers(
			genlayerBiomes,
			genlayerVoronoi,
			seed
		);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getSkyColor(Entity camera, float partialTicks) {

		// pale cyan methane atmosphere
		return Vec3.createVectorHelper(
			0.58,
			0.82,
			0.84
		);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getFogColor(
		float celestialAngle,
		float partialTicks
	) {

		// thicker blue-green haze
		return Vec3.createVectorHelper(
			0.36,
			0.58,
			0.62
		);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getSunBrightness(
		float partialTicks
	) {

		// sunlight is weak at Uranus
		return 0.035F;
	}

	@Override
	public float calculateCelestialAngle(
		long worldTime,
		float partialTicks
	) {

		// Uranus rotation:
		// ~17.2 Earth hours
		// slightly slower than Saturn

		long dayLength =
			20600L;

		long time =
			worldTime % dayLength;

		float angle =
			((float)time + partialTicks)
				/ (float)dayLength;

		// simulate weird axial tilt feel
		return (angle + 0.45F) % 1.0F;
	}

	@Override
	public boolean isSurfaceWorld() {
		return false;
	}

	@Override
	public boolean canRespawnHere() {
		return false;
	}

	@Override
	public boolean canCoordinateBeSpawn(
		int x,
		int z
	) {
		return false;
	}
}
