package com.hbm.handler.disease;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.util.CombatEntry;
import net.minecraft.util.CombatTracker;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public final class DiseaseEvents {
	// Forge 1.7.10 has no post-armor LivingDamageEvent. Read native confirmed wounds instead of
	// reimplementing special armor or treating the pre-armor LivingHurtEvent as proof of broken skin.
	private static final Field COMBAT_ENTRIES = ReflectionHelper.findField(CombatTracker.class, "combatEntries", "field_94556_a");
	private static final List<PendingBite> BITES = new ArrayList<PendingBite>();
	private static final Set<EntityBat> BATS = Collections.newSetFromMap(new IdentityHashMap<EntityBat, Boolean>());

	public static void queueBat(EntityBat bat) { BATS.add(bat); }

	public static void clear() {
		BITES.clear();
		BATS.clear();
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onHurt(LivingHurtEvent event) {
		if(event.entityLiving.worldObj.isRemote || event.ammount <= 0F || event.source.isProjectile()
				|| event.source.getEntity() != event.source.getSourceOfDamage()) return;
		if(!(event.source.getEntity() instanceof EntityLivingBase)) return;
		EntityLivingBase source = (EntityLivingBase) event.source.getEntity();
		// Vanilla wolf mob melee is a bite. Other hosts require our explicit marker, never generic melee inference.
		if(!(event.source instanceof BiteDamageSource)
				&& !(source.getClass() == EntityWolf.class && "mob".equals(event.source.getDamageType()))) return;
		// Damage attribution alone is not contact: native attack methods can be invoked at arbitrary range.
		double reachSquared = Math.min(9D, Math.max(1D, source.width * source.width * 4D + event.entityLiving.width));
		if(source.getDistanceSq(event.entityLiving.posX, event.entityLiving.boundingBox.minY, event.entityLiving.posZ) > reachSquared) return;
		if(DiseaseHost.get(source).infections.isEmpty()) return;
		BITES.add(new PendingBite(event, source));
	}

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if(event.phase != TickEvent.Phase.END) return;
		// Bat motion must be applied after EntityBat.updateAITasks, which overwrites task-based steering.
		for(EntityBat bat : BATS) {
			if(loaded(bat) && bat.isEntityAlive()) RabiesBehavior.updateBat(bat);
		}
		BATS.clear();
		for(PendingBite bite : BITES) {
			if(bite.wounded()) DiseaseManager.bitten(bite.source, bite.event.entityLiving);
		}
		BITES.clear(); // No retained entities, unload callbacks, forced chunk loads, or off-thread world mutations.
	}

	private static boolean loaded(EntityLivingBase host) {
		return host.addedToChunk && host.worldObj.getChunkProvider().chunkExists(host.chunkCoordX, host.chunkCoordZ);
	}

	@SuppressWarnings("unchecked")
	private static List<CombatEntry> entries(EntityLivingBase host) {
		try { return (List<CombatEntry>) COMBAT_ENTRIES.get(host.func_110142_aN()); }
		catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot read confirmed bite wounds", ex); }
	}

	private static final class PendingBite {
		final LivingHurtEvent event;
		final EntityLivingBase source;
		final CombatEntry previous;

		PendingBite(LivingHurtEvent event, EntityLivingBase source) {
			this.event = event;
			this.source = source;
			List<CombatEntry> entries = entries(event.entityLiving);
			previous = entries.isEmpty() ? null : entries.get(entries.size() - 1);
		}

		boolean wounded() {
			if(event.isCanceled() || event.ammount <= 0F || !event.entityLiving.isEntityAlive()
					|| !loaded(source) || !loaded(event.entityLiving)) return false;
			List<CombatEntry> entries = entries(event.entityLiving);
			for(int i = entries.size() - 1; i >= 0; i--) {
				CombatEntry entry = entries.get(i);
				if(entry == previous) break;
				if(entry.getDamageSrc() == event.source && entry.func_94563_c() > 0F) return true;
			}
			return false;
		}
	}
}
