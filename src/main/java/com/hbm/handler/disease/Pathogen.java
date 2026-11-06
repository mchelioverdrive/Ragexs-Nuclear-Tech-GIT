package com.hbm.handler.disease;

import net.minecraft.entity.EntityLivingBase;

/** Pathogen identity and host rules are independent of a host's infection and symptoms. Server thread only. */
public interface Pathogen {
	String id();
	boolean enabled();
	boolean supports(EntityLivingBase host);
	/** Additional establishment gates; host compatibility alone need not authorize infection. */
	default boolean canInfect(EntityLivingBase host) { return enabled() && supports(host); }
	void onHostTick(EntityLivingBase host, DiseaseHost data);
	Infection establish(EntityLivingBase host, String source, String sourceHost, long now);
	void progress(EntityLivingBase host, Infection infection, long now);
	void update(EntityLivingBase host, Infection infection, long now);
	void removeEffects(EntityLivingBase host);
	boolean infectious(Infection infection);
	double biteProbability(EntityLivingBase source, EntityLivingBase target);
}
