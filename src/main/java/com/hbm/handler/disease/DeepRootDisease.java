package com.hbm.handler.disease;

import java.util.UUID;

import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.IGeminiCreature;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.DeepRootVisualPacket;
import com.hbm.world.gemini.GeminiInvasionManager;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.WorldServer;

/** Loaded-host progression, independent of rabies' absolute-time deadlines and fatal behavior. */
public final class DeepRootDisease implements Pathogen {

	public static final String ID = "deep_root";
	public enum Stage { EXPOSURE, ROOTS, CONTRACTION, BULBS, SPROUTS, TERMINAL }
	private static final UUID SPEED = UUID.fromString("2b7366e1-4e42-48cb-a540-4c19d883fe16");
	private static final double[] SLOWING = {0, -0.10, -0.20, -0.30, -0.55, -0.60};

	@Override public String id() { return ID; }
	@Override public boolean enabled() { return GeminiConfig.enabled && GeminiConfig.deepRootEnabled; }

	@Override public boolean supports(EntityLivingBase host) {
		if(host instanceof IGeminiCreature || host.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD) return false;
		if(host instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) host;
			return !player.capabilities.disableDamage && !player.capabilities.isCreativeMode;
		}
		if(host instanceof EntityHorse && ((EntityHorse) host).getHorseType() >= 3) return false;
		// Known organic anatomy. Modded machines/monsters inheriting an animal class are not implicitly admitted.
		Class<?> type = host.getClass();
		return type == EntityVillager.class || type == EntityCow.class || type == EntityMooshroom.class
				|| type == EntityPig.class || type == EntitySheep.class || type == EntityHorse.class
				|| type == EntityWolf.class || type == EntityOcelot.class || type == EntityChicken.class;
	}

	@Override public boolean canInfect(EntityLivingBase host) {
		return enabled() && supports(host) && GeminiInvasionManager.canActivityOccur(host.worldObj, 2);
	}

	public static boolean active(EntityLivingBase host) {
		return host.isEntityAlive() && host.addedToChunk
				&& host.worldObj.getChunkProvider().chunkExists(host.chunkCoordX, host.chunkCoordZ)
				&& DiseaseManager.pathogen(ID).canInfect(host);
	}

	@Override public Infection establish(EntityLivingBase host, String source, String sourceHost, long now) {
		NBTTagCompound details = new NBTTagCompound();
		for(int i = 0; i < GeminiConfig.deepRootStageSeconds.length; i++)
			details.setLong("duration" + i, Math.max(1L, GeminiConfig.deepRootStageSeconds[i]) * 20L);
		details.setLong("stageTicks", 0L);
		return new Infection(ID, source, sourceHost, now, Stage.EXPOSURE.name(), "rooted", details);
	}

	public static Stage stage(Infection infection) {
		try { return Stage.valueOf(infection.stage); }
		catch(IllegalArgumentException ex) { return Stage.EXPOSURE; }
	}

	public static long stageTicks(Infection infection) {
		return Math.max(0L, infection.details.getLong("stageTicks"));
	}

	public static long durationTicks(Infection infection) {
		int index = stage(infection).ordinal();
		if(index == Stage.TERMINAL.ordinal()) return 0L; // Deliberately indefinite, never a death deadline.
		long saved = infection.details.getLong("duration" + index);
		return saved > 0L ? saved : Math.max(1L, GeminiConfig.deepRootStageSeconds[index]) * 20L;
	}

	@Override public void onHostTick(EntityLivingBase host, DiseaseHost data) {
		Infection infection = data.infections.get(ID);
		if(infection == null) {
			if(data.deepRootVisualStage != 0 || data.deepRootSpasm) removeEffects(host);
			return;
		}
		if(!active(host)) { removeEffects(host); return; }
		// Only actual loaded living updates count. Queries, tracking, offline time and reloads add no elapsed time.
		infection.details.setLong("stageTicks", Math.min(Long.MAX_VALUE - 1L, stageTicks(infection)) + 1L);
		progress(host, infection, 0L);
		// Queries and inspection APIs never convert; only this loaded-host callback may spawn.
		if(stage(infection) == Stage.TERMINAL && !(host instanceof EntityPlayer)
				&& (host.ticksExisted + host.getEntityId()) % 100 == 0
				&& com.hbm.world.gemini.MockerySpawning.convert(host)) return;
		Stage stage = stage(infection);
		long phase = stageTicks(infection) % 200L;
		boolean spasm = stage.ordinal() >= Stage.CONTRACTION.ordinal() && stageTicks(infection) >= 200L && phase < 6L;
		if(spasm && phase == 0L) {
			host.motionX *= -0.15D;
			host.motionZ *= -0.15D;
			host.velocityChanged = true; // A brief native velocity correction, never a position anchor or AI takeover.
		}
		// Six ticks per ten seconds at most. All stages retain movement between spasms, including terminal players.
		setSpeed(host, spasm ? -0.95D : SLOWING[stage.ordinal()]);
		setVisual(host, stage.ordinal() + 1, spasm);
	}

	@Override public void progress(EntityLivingBase host, Infection infection, long now) {
		if(!active(host)) return;
		while(stage(infection) != Stage.TERMINAL) {
			long duration = durationTicks(infection);
			if(stageTicks(infection) < duration) break;
			infection.details.setLong("stageTicks", stageTicks(infection) - duration);
			infection.stage = Stage.values()[stage(infection).ordinal() + 1].name();
		}
	}

	@Override public void update(EntityLivingBase host, Infection infection, long now) {
		if(!active(host) || !(host instanceof EntityPlayer) || stage(infection) == Stage.EXPOSURE) return;
		// Short, intermittent native visual symptom; potion transport handles the affected player's client.
		if(stageTicks(infection) % 600L < 20L) host.addPotionEffect(new PotionEffect(Potion.confusion.id, 100, 0));
	}

	private static void setSpeed(EntityLivingBase host, double amount) {
		IAttributeInstance attribute = host.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
		if(attribute == null) return;
		AttributeModifier previous = attribute.getModifier(SPEED);
		if(previous != null && previous.getAmount() == amount) return;
		if(previous != null) attribute.removeModifier(previous);
		if(amount != 0D) attribute.applyModifier(new AttributeModifier(SPEED, "Deep Root Disease", amount, 2).setSaved(false));
	}

	private static void setVisual(EntityLivingBase host, int stage, boolean spasm) {
		DiseaseHost data = DiseaseHost.get(host);
		if(data.deepRootVisualStage == stage && data.deepRootSpasm == spasm) return;
		data.deepRootVisualStage = stage;
		data.deepRootSpasm = spasm;
		DeepRootVisualPacket packet = new DeepRootVisualPacket(host);
		((WorldServer) host.worldObj).getEntityTracker().func_151247_a(host, PacketDispatcher.wrapper.getPacketFrom(packet));
		if(host instanceof EntityPlayerMP) PacketDispatcher.wrapper.sendTo(packet, (EntityPlayerMP) host);
	}

	/** Tracking/login synchronization computes presentation without advancing the saved clock. */
	public static void sendVisual(EntityLivingBase host, EntityPlayerMP observer) {
		Infection infection = DiseaseHost.get(host).infections.get(ID);
		if(infection == null && DiseaseHost.get(host).deepRootVisualStage == 0 && host != observer) return;
		int visual = 0;
		boolean spasm = false;
		if(infection != null && active(host)) {
			visual = stage(infection).ordinal() + 1;
			spasm = stage(infection).ordinal() >= Stage.CONTRACTION.ordinal()
					&& stageTicks(infection) >= 200L && stageTicks(infection) % 200L < 6L;
		}
		PacketDispatcher.wrapper.sendTo(new DeepRootVisualPacket(host, visual, spasm), observer);
	}

	@Override public void removeEffects(EntityLivingBase host) {
		if(host.worldObj.isRemote) return;
		setSpeed(host, 0D);
		setVisual(host, 0, false);
		// Short nausea expires naturally; never clear another system's same-ID potion effect.
	}

	// Patients are not carriers. Gemini carrier melee uses the shared API, not rabies' saliva/bite pathway.
	@Override public boolean infectious(Infection infection) { return false; }
	@Override public double biteProbability(EntityLivingBase source, EntityLivingBase target) { return 0D; }
}
