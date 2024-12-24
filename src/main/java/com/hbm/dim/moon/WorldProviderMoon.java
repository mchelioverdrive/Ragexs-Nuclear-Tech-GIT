package com.hbm.dim.moon;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.SpaceConfig;
import com.hbm.dim.WorldProviderCelestial;

import com.hbm.potion.HbmPotion;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.Random;

public class WorldProviderMoon extends WorldProviderCelestial {

	@Override
	public void registerWorldChunkManager() {
		this.worldChunkMgr = new WorldChunkManagerHell(new BiomeGenMoon(SpaceConfig.moonBiome), dimensionId);
	}

	@Override
	public String getDimensionName() {
		return "Mun";
	}

	@Override
	public IChunkProvider createChunkGenerator() {
		return new ChunkProviderMoon(this.worldObj, this.getSeed(), false);
	}

	@Override
	public Block getStone() {
		return ModBlocks.moon_rock;
	}

	@Override
	public void updateWeather() {
		super.updateWeather();

		// Apply radiation effect to players in the dimension
		if (!worldObj.isRemote) {
			Random rand = new Random();

			for (Object obj : worldObj.playerEntities) {
				if (obj instanceof EntityPlayer) {
					EntityPlayer player = (EntityPlayer) obj;

					// Check if the player can see the sky
					if (worldObj.canBlockSeeTheSky((int) player.posX, (int) player.posY, (int) player.posZ)) {
						// Apply radiation effect with a random chance
						if (rand.nextInt(100) == 0) {
							player.addPotionEffect(new PotionEffect(HbmPotion.radiation.id, 100, 1));
						}
					}
				}
			}
		}
	}
}


