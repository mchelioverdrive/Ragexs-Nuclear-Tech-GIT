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

/** Shared conversion/camp admission; no biome spawn entry, breeding or independent invasion producer. */
public final class FakePersonSpawning {

	/** Called once, after the captor's normal damage call confirms an uncanceled player death. */
	public static boolean convertPlayer(EntityWoodcrawler carrier, net.minecraft.entity.player.EntityPlayer victim) {
		World world = carrier.worldObj;
		if(world.isRemote || victim.worldObj != world || victim.isEntityAlive() || !carrier.isEntityAlive()) return false;
		EntityFakePerson person = new EntityFakePerson(world);
		if(!canSpawn(world, person)) return false;
		person.imitatePlayer(victim);
		// The original player, inventory, drops and respawn stay entirely in vanilla's death flow.
		for(int i = 0; i < 100; i++) {
			double x = Math.floor(victim.posX) + 0.5 + i % 5 - 2;
			double y = Math.floor(victim.boundingBox.minY) + i / 25 - 1;
			double z = Math.floor(victim.posZ) + 0.5 + i / 5 % 5 - 2;
			person.setLocationAndAngles(x, y, z, victim.rotationYaw, 0);
			if(y < 1 || y + person.height >= world.getHeight()
					|| !GardenerNests.loaded(world, person.boundingBox.expand(1, 1, 1))
					|| !world.func_147461_a(person.boundingBox).isEmpty() || world.isAnyLiquid(person.boundingBox)
					|| world.func_147461_a(person.boundingBox.addCoord(0, -0.1, 0)).isEmpty()) continue;
			boolean occupied = false;
			for(Object entry : world.getEntitiesWithinAABBExcludingEntity(person, person.boundingBox)) {
				Entity other = (Entity) entry;
				if(other != victim && !other.isDead && other.preventEntitySpawning) { occupied = true; break; }
			}
			if(occupied) continue;
			boolean spawned = world.spawnEntityInWorld(person);
			if(!spawned) GeminiPopulation.forgetFailedSpawn(person);
			return spawned; // Never repeat a Forge insertion veto at every candidate.
		}
		return false;
	}

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
		if(fake ? !canSpawn(event.world, null) : !WoodcrawlerSpawning.canConvert(event.world, event.cocoon)) return;
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
