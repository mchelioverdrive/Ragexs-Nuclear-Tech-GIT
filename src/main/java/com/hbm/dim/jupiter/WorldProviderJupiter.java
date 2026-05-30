package com.hbm.dim.jupiter;

import com.hbm.dim.WorldChunkManagerCelestial;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.dim.jupiter.GenLayerJupiter.GenLayerJupiterBiomes;
import com.hbm.dim.sun.ChunkProviderSun;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerVoronoiZoom;

public class WorldProviderJupiter extends WorldProviderCelestial {

	@Override
	public void registerWorldChunkManager() {

		this.worldChunkMgr =
			new WorldChunkManagerCelestial(
				createGigaHell(worldObj.getSeed())
			);
	}
	@Override
	public String getDimensionName() {
		return "Jupiter";
	}

	@Override
	public IChunkProvider createChunkGenerator() {
		return new ChunkProviderJupiter(this.worldObj, this.getSeed(), false);
	}

	@Override
	public void updateWeather() {

		this.worldObj.rainingStrength = 0.0F;
		this.worldObj.thunderingStrength = 1.0F;

		this.worldObj.getWorldInfo().setRaining(false);
		this.worldObj.getWorldInfo().setThundering(true);
	}

	private static WorldChunkManagerCelestial.BiomeGenLayers createGigaHell(long seed) {

		// base biome layer
		GenLayer genlayerBiomes =
			new GenLayerJupiterBiomes(seed);

		// smooth biome borders like vanilla
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
		return Vec3.createVectorHelper(0.86, 0.72, 0.55);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getFogColor(float celestialAngle, float partialTicks) {
		return Vec3.createVectorHelper(0.75, 0.62, 0.47);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getSunBrightness(float partialTicks) {
		return 0.08F;
	}

	@Override
	public float calculateCelestialAngle(long worldTime, float partialTicks) {

		long dayLength = 9900L; // ~9h 55m relative to Earth
		long time = worldTime % dayLength;

		return ((float) time + partialTicks) / (float) dayLength - 0.25F;
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
	public boolean canCoordinateBeSpawn(int x, int z) {
		return false;
	}




}
