package com.hbm.world.gemini;

import com.hbm.entity.mob.gemini.EntityWoodcrawler;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.entity.EntityLiving;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Server-thread extension hook on MinecraftForge.EVENT_BUS after 600 captive ticks,
 * retried every 200 ticks while unhandled. FakePersonSpawning supplies the default
 * conversion at lowest priority, respecting replacements supplied by integrations.
 * Supply a NEW, unspawned replacement in the carrier's world. The carrier owns
 * spawning it and only consumes its saved villager when that spawn succeeds.
 */
public final class WoodcrawlerConversionEvent extends Event {

	public final EntityWoodcrawler carrier;
	private final NBTTagCompound villager;
	private EntityLiving replacement;

	public WoodcrawlerConversionEvent(EntityWoodcrawler carrier, NBTTagCompound villager) {
		this.carrier = carrier;
		this.villager = (NBTTagCompound) villager.copy();
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
