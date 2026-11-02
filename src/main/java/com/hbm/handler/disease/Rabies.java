package com.hbm.handler.disease;

import java.util.ArrayList;

import com.hbm.config.RabiesConfig;
import com.hbm.potion.HbmPotion;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/** Viral mammal infection; timing, presentation frequency and per-bite risks are gameplay abstractions. */
public final class Rabies implements Pathogen {
	public static final String ID = "rabies";
	public static final long TREATMENT_TICKS = 24000L;
	private static final String FATAL_COMMITTED = "fatalCommitted";
	public static final DamageSource DISEASE_DAMAGE = new DamageSource("rabies").setDamageBypassesArmor().setDamageIsAbsolute();
	// Infection != infectiousness != visible illness. Terminal disease remains infectious until death.
	public enum Stage { INCUBATING, SHEDDING, CLINICAL, NEUROLOGICAL, TERMINAL, FATAL }

	@Override public String id() { return ID; }
	@Override public boolean enabled() { return RabiesConfig.enabled; }

	@Override public void onHostTick(EntityLivingBase host, DiseaseHost data) {
		if(host instanceof EntityBat && data.introductionsChecked.add(ID) && enabled() && supports(host)
				&& host.getRNG().nextDouble() < RabiesConfig.batchance)
			DiseaseManager.infect(host, this, "reservoir_entry", "Bat");
		// Check markers include encounters while disabled; clearing disease never rerolls entry infection.
		Infection infection = data.infections.get(ID);
		if(infection == null) return;
		// Commitment is saved even while disease behavior is disabled or the player is invulnerable.
		refreshFatalCommitment(host, infection, DiseaseManager.now(host));
		if(!enabled() || !supports(host)) {
			releaseBehavior(host);
			restoreFatalEffect(host, infection);
			return;
		}
		// Prepare before vanilla AI, not up to one symptom interval plus a scheduler interval later.
		progress(host, infection, DiseaseManager.now(host));
		restoreFatalEffect(host, infection);
		if(host instanceof EntityPlayer && symptomatic(infection) && RabiesConfig.playerWaterContactPenalty
				&& host.isInWater() && host.ticksExisted % 10 == 0) {
			// Optional gameplay penalty, not universal animal/human hydrophobia. Native water contact excludes rain.
			// Vanilla combine preserves stronger effects and longer same-strength effects; never remove them.
			host.addPotionEffect(new PotionEffect(Potion.weakness.id, 30, 0));
			host.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 30, 0));
		}
		if(host instanceof EntityLiving && (stage(infection).ordinal() >= Stage.CLINICAL.ordinal()
				|| host instanceof EntityBat && infectious(infection)))
			RabiesBehavior.updateHost((EntityLiving) host, infection);
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
		if(host instanceof EntityPlayer) timeline.setBoolean(FATAL_COMMITTED, false);
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
		refreshFatalCommitment(host, infection, now);
	}

	/** Missing legacy flags derive from the original infection time and monotonic stage. */
	public static void refreshFatalCommitment(EntityLivingBase host, Infection infection, long now) {
		if(host.worldObj.isRemote) return;
		if(host instanceof EntityPlayer && (treatmentTicksRemaining(infection, now) == 0L
				|| stage(infection).ordinal() >= Stage.CLINICAL.ordinal()))
			infection.details.setBoolean(FATAL_COMMITTED, true);
	}

	public static boolean fatalCommitted(Infection infection) {
		return infection.details.getBoolean(FATAL_COMMITTED);
	}

	public static long treatmentTicksRemaining(Infection infection, long now) {
		return Math.max(0L, TREATMENT_TICKS - Math.max(0L, now - infection.infectedAt));
	}

	/** Call progress with DiseaseManager.now before querying eligibility. */
	public static boolean treatmentEligible(Infection infection, long now) {
		return !fatalCommitted(infection) && treatmentTicksRemaining(infection, now) > 0L
				&& (stage(infection) == Stage.INCUBATING || stage(infection) == Stage.SHEDDING);
	}

	/** Server-only treatment; the record, not a potion or dose, owns eligibility and cleanup. */
	public static String treat(EntityPlayer player) {
		if(player.worldObj.isRemote || !player.isEntityAlive()) return "hrig.unavailable";
		Infection infection = DiseaseHost.get(player).infections.get(ID);
		if(infection == null) return "hrig.uninfected";
		long now = DiseaseManager.now(player);
		DiseaseManager.pathogen(ID).progress(player, infection, now);
		if(!treatmentEligible(infection, now)) {
			restoreFatalEffect(player, infection);
			return "hrig.too_late";
		}
		return DiseaseManager.remove(player, ID) ? "hrig.success" : "hrig.uninfected";
	}

	private static void restoreFatalEffect(EntityLivingBase host, Infection infection) {
		if(!(host instanceof EntityPlayer) || !fatalCommitted(infection)) return;
		PotionEffect current = host.getActivePotionEffect(HbmPotion.fatal);
		if(current == null || current.getDuration() < 100) {
			PotionEffect effect = new PotionEffect(HbmPotion.fatal.id, 200, 0);
			effect.setCurativeItems(new ArrayList<ItemStack>());
			host.addPotionEffect(effect);
		}
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

	public static boolean symptomatic(Infection infection) {
		Stage stage = stage(infection);
		return stage == Stage.CLINICAL || stage == Stage.NEUROLOGICAL || stage == Stage.TERMINAL;
	}

	static boolean swallowingSpasms(EntityPlayer player) {
		Infection infection = DiseaseHost.get(player).infections.get(ID);
		Pathogen rabies = DiseaseManager.pathogen(ID);
		if(infection == null || !rabies.enabled() || !rabies.supports(player) || !player.isEntityAlive()) return false;
		rabies.progress(player, infection, DiseaseManager.now(player));
		// CDC Yellow Book: human hydrophobia involves stimulus-triggered swallowing spasms.
		// This simplified restriction represents furious illness, not every paralytic presentation.
		return "furious".equals(infection.presentation) && symptomatic(infection);
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
		if(stage == Stage.TERMINAL) {
			long duration = Math.max(1L, infection.details.getLong("fatalAt") - infection.details.getLong("terminalAt"));
			host.attackEntityFrom(DISEASE_DAMAGE, (float) (host.getMaxHealth() * Math.min(0.5D, 10D / duration)));
		}
		if(stage == Stage.FATAL) host.attackEntityFrom(DISEASE_DAMAGE, Float.MAX_VALUE);
	}

	@Override public void removeEffects(EntityLivingBase host) {
		Infection infection = DiseaseHost.get(host).infections.get(ID);
		if(host instanceof EntityPlayer && infection != null && fatalCommitted(infection))
			host.removePotionEffect(HbmPotion.fatal.id);
		releaseBehavior(host);
	}

	private static void releaseBehavior(EntityLivingBase host) {
		Object behavior = DiseaseHost.get(host).behaviors.remove(ID);
		if(behavior instanceof RabiesBehavior && host instanceof EntityLiving) {
			((RabiesBehavior) behavior).release();
			((EntityLiving) host).tasks.removeTask((RabiesBehavior) behavior);
		} else RabiesBehavior.restoreSitting(host, DiseaseHost.get(host).infections.get(ID));
		// Our short symptom effects expire naturally; never remove another system's same-ID potion effects.
	}

	public static String behaviorStatus(EntityLivingBase host, Infection infection) {
		return RabiesBehavior.describe(host, infection);
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
