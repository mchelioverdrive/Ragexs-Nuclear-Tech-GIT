package com.hbm.handler.disease;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

/** Explicit saliva-bearing bite, with ordinary mob damage/protection semantics. Not generic mammal melee. */
public final class BiteDamageSource extends EntityDamageSource {
	public BiteDamageSource(EntityLivingBase source) { super("mob", source); }
}
