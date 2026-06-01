package com.hbm.dim.neptune.biome;

import com.hbm.dim.BiomeGenBaseCelestial;

public class BiomeGenNeptune extends BiomeGenBaseCelestial {

	public BiomeGenNeptune(int id) {
		super(id);
		//TODO check if right

		this.rootHeight = 8.0F;
		this.heightVariation = 0.0F;

		this.rainfall = 0.0F;
		this.temperature = 0.0F;

		this.enableRain = false;

		this.theBiomeDecorator.treesPerChunk = 0;
		this.theBiomeDecorator.flowersPerChunk = 0;
		this.theBiomeDecorator.grassPerChunk = 0;
		this.theBiomeDecorator.deadBushPerChunk = 0;
		this.theBiomeDecorator.mushroomsPerChunk = 0;
		this.theBiomeDecorator.reedsPerChunk = 0;
		this.theBiomeDecorator.cactiPerChunk = 0;
		this.theBiomeDecorator.sandPerChunk = 0;
		this.theBiomeDecorator.sandPerChunk2 = 0;
		this.theBiomeDecorator.clayPerChunk = 0;

		this.spawnableCreatureList.clear();
		this.spawnableMonsterList.clear();
		this.spawnableWaterCreatureList.clear();
		this.spawnableCaveCreatureList.clear();
	}

}
