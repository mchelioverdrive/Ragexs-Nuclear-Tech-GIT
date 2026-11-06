package com.hbm.handler.disease;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public final class DiseaseManager {
	private static final Map<String, Pathogen> PATHOGENS = new LinkedHashMap<String, Pathogen>();
	static { register(new Rabies()); register(new DeepRootDisease()); }

	public static void register(Pathogen pathogen) {
		if(PATHOGENS.containsKey(pathogen.id())) throw new IllegalArgumentException("Duplicate pathogen " + pathogen.id());
		PATHOGENS.put(pathogen.id(), pathogen);
	}

	/** Overworld total ticks: no /time or sleep jumps, no chunk loads, no wall-clock/offline-server progression. */
	public static long now(EntityLivingBase host) {
		WorldServer overworld = DimensionManager.getWorld(0);
		return (overworld != null ? overworld : host.worldObj).getTotalWorldTime();
	}

	public static Pathogen pathogen(String id) { return PATHOGENS.get(id); }

	public static boolean infect(EntityLivingBase host, Pathogen pathogen, String source, String sourceHost) {
		if(host.worldObj.isRemote || !pathogen.canInfect(host) || !host.isEntityAlive()) return false;
		DiseaseHost data = DiseaseHost.get(host);
		if(data.infections.containsKey(pathogen.id())) return false;
		data.infections.put(pathogen.id(), pathogen.establish(host, source, sourceHost, now(host)));
		return true;
	}

	public static void tick(EntityLivingBase host) {
		if(host.worldObj.isRemote || !host.isEntityAlive()) return;
		DiseaseHost data = DiseaseHost.get(host);
		for(Pathogen pathogen : PATHOGENS.values()) pathogen.onHostTick(host, data);
		// Stagger symptom updates; each pathogen owns its clock (rabies deadlines, Deep Root loaded ticks).
		if(data.infections.isEmpty() || (host.ticksExisted + host.getEntityId()) % 20 != 0) return;
		long now = now(host);
		for(Infection infection : data.infections.values()) {
			Pathogen pathogen = pathogen(infection.pathogen);
			if(pathogen == null) continue; // Preserve records for unavailable future integrations.
			if(!pathogen.enabled() || !pathogen.supports(host)) {
				// onHostTick already releases inactive behavior; keep persistent status reconciliation there.
				continue;
			}
			pathogen.progress(host, infection, now);
			pathogen.update(host, infection, now);
		}
	}

	public static boolean remove(EntityLivingBase host, String id) {
		if(host.worldObj.isRemote) return false;
		Pathogen pathogen = pathogen(id);
		if(pathogen != null) pathogen.removeEffects(host);
		return DiseaseHost.get(host).infections.remove(id) != null;
	}

	public static void clonePlayer(EntityLivingBase original, EntityLivingBase replacement, boolean wasDeath) {
		// Respawn clears disease as an explicit gameplay rule; dimension clones retain exact records/timers.
		if(!wasDeath) {
			NBTTagCompound tag = new NBTTagCompound();
			DiseaseHost.get(original).saveNBTData(tag);
			DiseaseHost.get(replacement).loadNBTData(tag);
		} else {
			pathogen(Rabies.ID).removeEffects(replacement);
			pathogen(DeepRootDisease.ID).removeEffects(replacement);
			DiseaseHost.get(replacement).infections.clear();
		}
	}

	public static void bitten(EntityLivingBase source, EntityLivingBase target) {
		if(source.worldObj.isRemote || !target.isEntityAlive()) return;
		long now = now(source);
		boolean rabiesBite = false;
		for(Infection infection : DiseaseHost.get(source).infections.values()) {
			// Gameplay rule: villagers remain susceptible and aggressive, but never transmit rabies.
			if(source instanceof EntityVillager && Rabies.ID.equals(infection.pathogen)) continue;
			Pathogen pathogen = pathogen(infection.pathogen);
			if(pathogen == null || !pathogen.enabled() || !pathogen.supports(source) || !pathogen.supports(target)) continue;
			pathogen.progress(source, infection, now);
			if(!pathogen.infectious(infection)) continue;
			if(Rabies.ID.equals(infection.pathogen)) rabiesBite = true;
			if(DiseaseHost.get(target).infections.containsKey(pathogen.id())) continue;
			double probability = pathogen.biteProbability(source, target);
			// Exactly one establishment roll per confirmed wounding bite, never per contact tick or damage point.
			if(target.getRNG().nextDouble() < probability)
				infect(target, pathogen, "bite:" + source.getUniqueID(), net.minecraft.entity.EntityList.getEntityString(source));
		}
		// Wound confirmation owns this call. Finish transmission first, including its sole establishment roll.
		if(rabiesBite) RabiesAvoidance.bitten(target, source);
	}
}
