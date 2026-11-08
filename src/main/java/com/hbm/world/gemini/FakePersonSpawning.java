package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityFakePerson;
import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import net.minecraft.entity.EntityLiving;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/** Conversion only: no biome spawn entry, breeding, infection or independent invasion producer. */
public final class FakePersonSpawning {

	public static boolean canSpawn(World world, EntityFakePerson candidate) {
		if(GeminiSpawning.manualAdmission(candidate)) return true;
		if(!GeminiInvasionManager.canActivityOccur(world) || world.difficultySetting == EnumDifficulty.PEACEFUL
				|| GeminiConfig.fakePersonMaxLoaded == 0) return false;
		return GeminiPopulation.canAdmit(world, EntityFakePerson.class, candidate, GeminiConfig.fakePersonMaxLoaded);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onConvert(WoodcrawlerConversionEvent event) {
		if(event.getReplacement() != null) return;
		boolean fake = event.shouldConvertToFakePerson();
		if(fake ? !canSpawn(event.world, null) : !WoodcrawlerSpawning.canConvert(event.world)) return;
		NBTTagCompound victim = event.getVillagerData();
		// Unknown modded villager subclasses keep the lossless captive/release path.
		// Never accept player NBT or a Gemini replacement as a new victim.
		if(EntityList.stringToClassMapping.get(victim.getString("id")) != EntityVillager.class) return;
		EntityLiving replacement;
		if(fake) {
			EntityFakePerson person = new EntityFakePerson(event.world);
			person.imitateVillager(victim);
			replacement = person;
		} else {
			EntityWoodcrawler crawler = new EntityWoodcrawler(event.world);
			crawler.inheritConversionIdentity(victim);
			crawler.setCrouching(true); // A house cocoon must not require full standing clearance.
			crawler.crouchBlend = crawler.prevCrouchBlend = 1;
			replacement = crawler;
		}
		event.setReplacement(replacement);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onJoin(EntityJoinWorldEvent event) {
		if(event.world.isRemote || !(event.entity instanceof EntityFakePerson)) return;
		EntityFakePerson mob = (EntityFakePerson) event.entity;
		// Chunk loads already have membership; fresh spawns (even /summon with NBT) do not.
		if(!mob.addedToChunk && !canSpawn(event.world, mob)) {
			event.setCanceled(true);
			return;
		}
		// A conversion must not duplicate a restored villager's identity. This is an admission-only
		// check, never a ticking population search; Gemini duplicates use the shared registry.
		if(!mob.addedToChunk) for(Object entry : event.world.loadedEntityList) {
			Entity other = (Entity) entry;
			if(other != mob && !other.isDead && mob.getUniqueID().equals(other.getUniqueID())
					&& !com.hbm.entity.mob.gemini.EntityWoodcrawlerCocoon.isConversionSource(other, mob)) {
				event.setCanceled(true); return;
			}
		}
	}
}
