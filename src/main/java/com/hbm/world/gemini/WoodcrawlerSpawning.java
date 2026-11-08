package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

public final class WoodcrawlerSpawning {

	public static boolean isForest(World world, int x, int z) {
		return world.provider.dimensionId == 0 && world.blockExists(x, 0, z)
				&& BiomeDictionary.isBiomeOfType(world.getBiomeGenForCoords(x, z), BiomeDictionary.Type.FOREST);
	}

	public static boolean canSpawn(World world, EntityWoodcrawler candidate) {
		if(GeminiSpawning.manualAdmission(candidate)) return true;
		if(!GeminiInvasionManager.canActivityOccur(world) || GeminiConfig.woodcrawlerSpawnWeight == 0
				|| GeminiConfig.woodcrawlerMaxLoaded == 0 || world.difficultySetting == EnumDifficulty.PEACEFUL) return false;
		return GeminiPopulation.canAdmit(world, EntityWoodcrawler.class, candidate, GeminiConfig.woodcrawlerMaxLoaded);
	}
	public static boolean canConvert(World world) {
		return GeminiInvasionManager.canActivityOccur(world) && world.difficultySetting != EnumDifficulty.PEACEFUL
				&& GeminiPopulation.canAdmit(world, EntityWoodcrawler.class, null, GeminiConfig.woodcrawlerMaxLoaded);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
		if(event.entityLiving instanceof EntityWoodcrawler && (!canSpawn(event.world, (EntityWoodcrawler) event.entityLiving)
				|| !isForest(event.world, MathHelper.floor_float(event.x), MathHelper.floor_float(event.z))
				|| event.world.rand.nextInt(100) >= GeminiInvasionManager.encounterPercent(event.world))) {
			event.setResult(Event.Result.DENY);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onJoin(EntityJoinWorldEvent event) {
		if(event.world.isRemote || !(event.entity instanceof EntityWoodcrawler)) return;
		EntityWoodcrawler mob = (EntityWoodcrawler) event.entity;
		if(!mob.addedToChunk && !(mob.getEntityData().getBoolean("WoodcrawlerConverted")
				? canConvert(event.world) : canSpawn(event.world, mob))) event.setCanceled(true);
	}

	@SubscribeEvent
	public void onInvasion(GeminiInvasionManager.InvasionEvent event) {
		WorldServer world = event.world;
		if(!GeminiInvasionManager.canActivityOccur(world) || !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
				|| world.playerEntities.isEmpty() || !canSpawn(world, null)) return;
		EntityPlayer player = (EntityPlayer) world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
		if(player.isDead) return;
		EntityWoodcrawler mob = new EntityWoodcrawler(world);
		// One creature at most, eight loaded forest columns, no chunk generation or missed-event replay.
		for(int attempt = 0; attempt < 8; attempt++) {
			double angle = world.rand.nextDouble() * Math.PI * 2;
			int radius = 28 + world.rand.nextInt(29);
			int x = MathHelper.floor_double(player.posX + Math.cos(angle) * radius);
			int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * radius);
			if(!isForest(world, x, z) || world.getClosestPlayer(x, player.posY, z, 24) != null) continue;
			int y = world.getTopSolidOrLiquidBlock(x, z);
			mob.setLocationAndAngles(x + 0.5D, y, z + 0.5D, world.rand.nextFloat() * 360, 0);
			if(!mob.getCanSpawnHere()) continue;
			GeminiSpawning.markNatural(mob);
			mob.onSpawnWithEgg(null);
			if(world.spawnEntityInWorld(mob)) return;
		}
	}
}
