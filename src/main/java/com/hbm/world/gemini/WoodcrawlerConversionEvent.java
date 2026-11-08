package com.hbm.world.gemini;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;
import com.hbm.entity.mob.gemini.EntityWoodcrawlerCocoon;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * Server-thread extension hook on MinecraftForge.EVENT_BUS after the saved processing delay,
 * retried every 100 ticks, pausing failure time while population-limited. FakePersonSpawning supplies the default
 * conversion at lowest priority, respecting replacements supplied by integrations.
 * Supply a NEW, unspawned replacement in world. The cocoon owns
 * spawning it and only consumes its saved villager when that spawn succeeds.
 */
public final class WoodcrawlerConversionEvent extends Event {

	/** Legacy event source; null for the new independent processing cocoons. */
	public final EntityWoodcrawler carrier;
	public final EntityWoodcrawlerCocoon cocoon;
	public final World world;
	private final NBTTagCompound villager;
	private EntityLiving replacement;

	public WoodcrawlerConversionEvent(EntityWoodcrawler carrier, NBTTagCompound villager) {
		this.carrier = carrier;
		this.cocoon = null;
		this.world = carrier.worldObj;
		this.villager = (NBTTagCompound) villager.copy();
	}

	public WoodcrawlerConversionEvent(EntityWoodcrawlerCocoon cocoon, NBTTagCompound villager) {
		this.carrier = null;
		this.cocoon = cocoon;
		this.world = cocoon.worldObj;
		this.villager = (NBTTagCompound) villager.copy();
	}

	public boolean shouldConvertToFakePerson() {
		return cocoon != null ? cocoon.shouldConvertToFakePerson() : carrier.shouldConvertToFakePerson();
	}

	public NBTTagCompound getVillagerData() {
		return (NBTTagCompound) villager.copy();
	}

	public void setReplacement(EntityLiving replacement) {
		this.replacement = replacement;
	}

	public EntityLiving getReplacement() {
		return replacement;
	}
}
