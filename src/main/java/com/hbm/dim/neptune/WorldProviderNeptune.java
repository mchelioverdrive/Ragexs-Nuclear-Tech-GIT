package com.hbm.dim.neptune;

import com.hbm.dim.WorldChunkManagerCelestial;
import com.hbm.dim.WorldProviderCelestial;
import com.hbm.dim.saturn.ChunkProviderSaturn;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.IChunkProvider;

public class WorldProviderNeptune extends WorldProviderCelestial {

	@Override
	public void registerWorldChunkManager() {

		this.worldChunkMgr =
			new WorldChunkManagerCelestial(
				createNeptune(worldObj.getSeed())
			);
	}
	@Override
	public String getDimensionName() {
		return "Neptune";
	}
	@Override
	public IChunkProvider createChunkGenerator() {
		return new ChunkProviderNeptune(this.worldObj, this.getSeed(), false);
	}

	@Override
	public void updateWeather() {

		worldObj.getWorldInfo().setRaining(true);
		worldObj.getWorldInfo().setThundering(true);
	}

	private static WorldChunkManagerCelestial.BiomeGenLayers createNeptune(long seed) {
		//TODO
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getSkyColor(Entity camera, float partialTicks) {

		return Vec3.createVectorHelper(
			0.08,
			0.20,
			0.45
		);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Vec3 getFogColor(float celestialAngle, float partialTicks) {

		return Vec3.createVectorHelper(
			0.03,
			0.09,
			0.20
		);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getSunBrightness(float partialTicks) {

		return 0.06F;
	}

	@Override
	public float calculateCelestialAngle(long worldTime, float partialTicks) {

		int dayLength = 16000;

		long time =
			worldTime % dayLength;

		return ((float)time + partialTicks)
			/ (float)dayLength - 0.25F;
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
