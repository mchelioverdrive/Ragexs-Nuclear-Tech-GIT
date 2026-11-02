package com.hbm.handler.disease;

import com.hbm.config.RabiesConfig;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/** Viral mammal infection; timing, presentation frequency and per-bite risks are gameplay abstractions. */
public final class Rabies implements Pathogen {
	public static final String ID = "rabies";
	public static final DamageSource DISEASE_DAMAGE = new DamageSource("rabies").setDamageBypassesArmor().setDamageIsAbsolute();
	// Infection != infectiousness != visible illness. Terminal disease remains infectious until death.
	public enum Stage { INCUBATING, SHEDDING, CLINICAL, NEUROLOGICAL, TERMINAL, FATAL }

	@Override public String id() { return ID; }
	@Override public boolean enabled() { return RabiesConfig.enabled; }

	@Override public void onHostTick(EntityLivingBase host, DiseaseHost data) {
		if(!(host instanceof EntityBat)) return;
		if(data.introductionsChecked.add(ID) && enabled() && supports(host)
				&& host.getRNG().nextDouble() < RabiesConfig.batchance)
			DiseaseManager.infect(host, this, "reservoir_entry", "Bat");
		// Check markers include encounters while disabled; clearing disease never rerolls entry infection.
		if(enabled() && data.infections.containsKey(ID) && (host.ticksExisted + host.getEntityId()) % 5 == 0)
			DiseaseEvents.queueBat((EntityBat) host);
	}

	@Override public boolean supports(EntityLivingBase host) {
		if(host.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD || host.getCreatureAttribute() == EnumCreatureAttribute.ARTHROPOD
				|| host instanceof EntityGolem || host instanceof EntityWaterMob || host instanceof EntityChicken) return false;
		if(host instanceof EntityHorse && ((EntityHorse) host).getHorseType() >= 3) return false; // undead equines
		if(host instanceof EntityPlayer) return !((EntityPlayer) host).capabilities.disableDamage;
		Class<?> type = host.getClass();
		// Exact vanilla classes avoid admitting modded machines simply because they subclass a cow/wolf.
		return type == EntityBat.class || type == EntityWolf.class || type == EntityCow.class || type == EntityMooshroom.class
				|| type == EntityPig.class || type == EntitySheep.class || type == EntityHorse.class
				|| type == EntityOcelot.class || type == EntityVillager.class
				|| RabiesConfig.mammalAllowlist.contains(EntityList.getEntityString(host));
	}

	@Override public Infection establish(EntityLivingBase host, String source, String sourceHost, long now) {
		NBTTagCompound timeline = new NBTTagCompound();
		long shedding = now + RabiesConfig.incubationTicks;
		long clinical = shedding + RabiesConfig.infectiousTicks;
		long neurological = clinical + RabiesConfig.clinicalTicks / 2;
		long terminal = clinical + RabiesConfig.clinicalTicks;
		timeline.setLong("sheddingAt", shedding);
		timeline.setLong("clinicalAt", clinical);
		timeline.setLong("neurologicalAt", neurological);
		timeline.setLong("terminalAt", terminal);
		timeline.setLong("fatalAt", terminal + RabiesConfig.terminalTicks);
		return new Infection(ID, source, sourceHost, now, Stage.INCUBATING.name(),
				host.getRNG().nextDouble() < RabiesConfig.furiousChance ? "furious" : "paralytic", timeline);
	}

	@Override public void progress(EntityLivingBase host, Infection infection, long now) {
		NBTTagCompound time = infection.details;
		Stage stage = now >= time.getLong("fatalAt") ? Stage.FATAL
				: now >= time.getLong("terminalAt") ? Stage.TERMINAL
				: now >= time.getLong("neurologicalAt") ? Stage.NEUROLOGICAL
				: now >= time.getLong("clinicalAt") ? Stage.CLINICAL
				: now >= time.getLong("sheddingAt") ? Stage.SHEDDING : Stage.INCUBATING;
		// Persisted monotonic stage also prevents an administrator's clock rollback from reversing illness.
		if(stage.ordinal() > stage(infection).ordinal()) infection.stage = stage.name();
	}

	public static Stage stage(Infection infection) {
		try { return Stage.valueOf(infection.stage); }
		catch(IllegalArgumentException ex) { return Stage.INCUBATING; }
	}

	@Override public boolean infectious(Infection infection) {
		Stage stage = stage(infection);
		return stage != Stage.INCUBATING && stage != Stage.FATAL;
	}

	public static boolean furious(Infection infection) {
		Stage stage = stage(infection);
		return "furious".equals(infection.presentation) && (stage == Stage.CLINICAL || stage == Stage.NEUROLOGICAL);
	}

	@Override public void update(EntityLivingBase host, Infection infection, long now) {
		Stage stage = stage(infection);
		if(stage.ordinal() >= Stage.CLINICAL.ordinal()) {
			// Vanilla potion transport synchronizes symptoms only. Clearing potions never clears the record.
			host.addPotionEffect(new PotionEffect(Potion.weakness.id, 40, stage == Stage.CLINICAL ? 0 : 1));
			if(!furious(infection) || stage.ordinal() >= Stage.NEUROLOGICAL.ordinal())
				host.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, stage.ordinal() >= Stage.TERMINAL.ordinal() ? 4 : 1));
			if(host instanceof EntityPlayer && stage.ordinal() >= Stage.NEUROLOGICAL.ordinal())
				host.addPotionEffect(new PotionEffect(Potion.confusion.id, 100, 0));
		}
		if(host instanceof EntityLiving && stage.ordinal() >= Stage.CLINICAL.ordinal()
				&& !(host instanceof EntityBat) && !DiseaseHost.get(host).behaviors.containsKey(ID)) {
			RabiesBehavior behavior = new RabiesBehavior((EntityLiving) host);
			DiseaseHost.get(host).behaviors.put(ID, behavior);
			((EntityLiving) host).tasks.addTask(0, behavior);
		}
		if(stage == Stage.TERMINAL) {
			long duration = Math.max(1L, infection.details.getLong("fatalAt") - infection.details.getLong("terminalAt"));
			host.attackEntityFrom(DISEASE_DAMAGE, (float) (host.getMaxHealth() * Math.min(0.5D, 10D / duration)));
		}
		if(stage == Stage.FATAL) host.attackEntityFrom(DISEASE_DAMAGE, Float.MAX_VALUE);
	}

	@Override public void removeEffects(EntityLivingBase host) {
		Object behavior = DiseaseHost.get(host).behaviors.remove(ID);
		if(behavior instanceof RabiesBehavior && host instanceof EntityLiving)
			((EntityLiving) host).tasks.removeTask((RabiesBehavior) behavior);
		// Our short symptom effects expire naturally; never remove another system's same-ID potion effects.
	}

	@Override public double biteProbability(EntityLivingBase source, EntityLivingBase target) {
		if(source instanceof EntityBat) {
			if(target instanceof EntityPlayer) return RabiesConfig.batToPlayer;
			return target instanceof EntityWolf ? RabiesConfig.dogchance : RabiesConfig.batToMammal;
		}
		if(source instanceof EntityWolf) {
			if(target instanceof EntityPlayer) return RabiesConfig.wolfToPlayer;
			return target instanceof EntityWolf ? RabiesConfig.wolfToWolf : RabiesConfig.wolfToMammal;
		}
		return target instanceof EntityPlayer ? RabiesConfig.mammalToPlayer : RabiesConfig.mammalToMammal;
	}
}
