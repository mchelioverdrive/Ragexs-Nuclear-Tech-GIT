package com.hbm.world.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityFakePerson;

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
		if(!GeminiInvasionManager.canActivityOccur(world) || world.difficultySetting == EnumDifficulty.PEACEFUL
				|| GeminiConfig.fakePersonMaxLoaded == 0) return false;
		int count = 0;
		for(Object entry : world.loadedEntityList) {
			if(entry instanceof EntityFakePerson && entry != candidate && !((Entity) entry).isDead
					&& ++count >= GeminiConfig.fakePersonMaxLoaded) return false;
		}
		return true;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onConvert(WoodcrawlerConversionEvent event) {
		if(event.getReplacement() != null || !event.carrier.shouldConvertToFakePerson()
				|| !canSpawn(event.carrier.worldObj, null)) return;
		NBTTagCompound victim = event.getVillagerData();
		// Unknown modded villager subclasses keep the lossless captive/release path.
		// Never accept player NBT or a Gemini replacement as a new victim.
		if(EntityList.stringToClassMapping.get(victim.getString("id")) != EntityVillager.class) return;
		EntityFakePerson replacement = new EntityFakePerson(event.carrier.worldObj);
		replacement.imitateVillager(victim);
		event.setReplacement(replacement);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onJoin(EntityJoinWorldEvent event) {
		if(event.world.isRemote || !(event.entity instanceof EntityFakePerson)) return;
		EntityFakePerson mob = (EntityFakePerson) event.entity;
		// Chunk loads already have membership; fresh spawns (even /summon with NBT) do not.
		if(!GeminiInvasionManager.canActivityOccur(event.world) || event.world.difficultySetting == EnumDifficulty.PEACEFUL
				|| !mob.addedToChunk && !canSpawn(event.world, mob)) {
			event.setCanceled(true);
			return;
		}
		// Conversions retain the victim's identity. Reject a second living copy.
		for(Object entry : event.world.loadedEntityList) {
			Entity other = (Entity) entry;
			if(other != mob && !other.isDead && mob.getUniqueID().equals(other.getUniqueID())) {
				event.setCanceled(true);
				return;
			}
		}
	}
}
