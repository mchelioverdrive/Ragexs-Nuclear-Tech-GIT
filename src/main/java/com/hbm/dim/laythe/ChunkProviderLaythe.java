package com.hbm.dim.laythe;

import java.util.ArrayList;
import java.util.List;

import com.hbm.blocks.ModBlocks;
import com.hbm.dim.ChunkProviderCelestial;
import com.hbm.dim.laythe.biome.BiomeGenBaseLaythe;
import com.hbm.dim.mapgen.MapGenGreg;
import com.hbm.dim.mapgen.MapGenTiltedSpires;
import com.hbm.entity.mob.EntityCreeperFlesh;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase.SpawnListEntry;

public class ChunkProviderLaythe extends ChunkProviderCelestial {

	private MapGenGreg caveGenV3 = new MapGenGreg();
	private MapGenTiltedSpires spires = new MapGenTiltedSpires(2, 14, 0.8F);
	private MapGenTiltedSpires snowires = new MapGenTiltedSpires(2, 14, 0.8F);

	private List<SpawnListEntry> spawnedOfFlesh = new ArrayList<SpawnListEntry>();

	public ChunkProviderLaythe(World world, long seed, boolean hasMapFeatures) {
		super(world, seed, hasMapFeatures);

		spires.rock = Blocks.stone;
		spires.regolith = ModBlocks.laythe_silt;
		spires.curve = true;
		spires.maxPoint = 6.0F;
		spires.maxTilt = 3.5F;

		seaBlock = Blocks.water;

		//todo change:
		spawnedOfFlesh.add(new SpawnListEntry(EntityCreeperFlesh.class, 10, 4, 4));

		snowires.rock = Blocks.packed_ice;
		snowires.regolith = Blocks.snow;
		snowires.curve = true;
		snowires.maxPoint = 6.0F;
		snowires.maxTilt = 3.5F;

	}

	@Override
	public BlockMetaBuffer getChunkPrimer(int x, int z) {
		BlockMetaBuffer buffer = super.getChunkPrimer(x, z);

		spires.func_151539_a(this, worldObj, x, z, buffer.blocks);
		caveGenV3.func_151539_a(this, worldObj, x, z, buffer.blocks);
		if(biomesForGeneration[0] == BiomeGenBaseLaythe.laythePolar) {
			snowires.func_151539_a(this, worldObj, x, z, buffer.blocks);
		}

		return buffer;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public List getPossibleCreatures(EnumCreatureType creatureType, int x, int y, int z) {
		if(creatureType == EnumCreatureType.monster && worldObj.getBlock(x, y - 1, z) == ModBlocks.tumor)
			return spawnedOfFlesh;

		//world, player, EntityFRIEND, setPosition, and getCanSpawnHere are NOT defined in this class. Define them or fix this.
		//if (!world.isRemote) {
		//	if (world.rand.nextInt(5000) == 0) { // rare event
//
		//		// check if one already exists
		//		for (Object obj : world.loadedEntityList) {
		//			if (obj instanceof EntityFRIEND) return;
		//		}
//
		//		EntityFRIEND friend = new EntityFRIEND(world);
//
		//		double x = player.posX + (rand.nextDouble() - 0.5) * 50;
		//		double y = player.posY;
		//		double z = player.posZ + (rand.nextDouble() - 0.5) * 50;
//
		//		friend.setPosition(x, y, z);
//
		//		if (friend.getCanSpawnHere()) {
		//			world.spawnEntityInWorld(friend);
		//		}
		//	}
		//}

		return super.getPossibleCreatures(creatureType, x, y, z);
	}

}
