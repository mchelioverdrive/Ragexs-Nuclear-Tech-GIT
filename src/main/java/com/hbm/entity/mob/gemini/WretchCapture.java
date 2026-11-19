package com.hbm.entity.mob.gemini;

import com.hbm.config.GeminiConfig;
import com.hbm.handler.GeminiCaptures;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/** One transient live victim; normal cancelable damage/death owns every inventory and drop. */
final class WretchCapture {
    private final EntityWretch boss;
    private EntityLivingBase victim;
    private int sneakTicks;
    private boolean exposure, completing;
    private LivingDeathEvent attemptedDeath;
    WretchCapture(EntityWretch boss) { this.boss = boss; }
    boolean owns(EntityLivingBase entity) { return victim == entity; }
    boolean eligible(EntityLivingBase entity) {
        if(entity == null || !entity.isEntityAlive() || entity.worldObj != boss.worldObj || !entity.addedToChunk
                || entity.ridingEntity != null || entity.riddenByEntity != null || entity.isEntityInvulnerable()
                || entity.noClip || entity instanceof IGeminiCreature || entity instanceof IBossDisplayData
                || entity.width > 1.1F || entity.height > 2.6F || entity.getEntityData().getBoolean("WretchProtected")
                || entity.getEntityData().hasKey("MockeryCaptor") || entity.getEntityData().hasKey("GardenerCaptor")
                || !owns(entity) && !GeminiCaptures.available(entity)
                || !boss.inTerritory(entity.posX, entity.posZ, boss.territoryRadius())
                || boss.worldObj.getEntityByID(entity.getEntityId()) != entity) return false;
        NBTTagCompound tag = entity.getEntityData();
        if(tag.getLong("WoodcrawlerCaptorUntil") > boss.worldObj.getTotalWorldTime()) return false;
        if(tag.getLong("WretchCaptorUntil") > boss.worldObj.getTotalWorldTime() && !boss.getUniqueID().toString().equals(tag.getString("WretchCaptor"))) return false;
        if(entity instanceof EntityPlayerMP && ((EntityPlayerMP) entity).playerNetServerHandler == null) return false;
        if(entity instanceof EntityPlayer) return boss.validPlayer(entity);
        // Mod humanoids explicitly opt in. Animal inheritance never admits a machine, pet or mount.
        return entity instanceof EntityLiving && !((EntityLiving) entity).getLeashed()
                && !entity.isChild() && (entity.getClass() == EntityVillager.class || tag.getBoolean("WretchHumanoid"));
    }
    boolean take(EntityLivingBase entity) {
        if(victim != null || !eligible(entity) || entity.getEntityData().getLong("WretchEscapeUntil") > boss.worldObj.getTotalWorldTime()) return false;
        float before = entity.getHealth();
        if(!entity.attackEntityFrom(DamageSource.causeMobDamage(boss), 2F) || !entity.isEntityAlive() || entity.getHealth() >= before) return false;
        if(!GeminiCaptures.attach(boss, entity, false, this::interrupt)) return false;
        victim = entity; sneakTicks = 0; exposure = false; tag(); boss.captiveId(entity.getEntityId()); boss.begin(EntityWretch.HOLD);
        GeminiCaptures.diagnostic(boss, victim, "hold", "Initial attachment", 0, boss.pose(1).captureAnchor());
        boss.voice("grab", 1.2F);
        if(entity instanceof EntityPlayer) ((EntityPlayer) entity).addChatMessage(new ChatComponentTranslation("wretch.capture.escape"));
        return true;
    }
    private void tag() {
        NBTTagCompound tag = victim.getEntityData(); tag.setString("WretchCaptor", boss.getUniqueID().toString());
        tag.setInteger("WretchCaptorId", boss.getEntityId()); tag.setLong("WretchCaptorUntil", boss.worldObj.getTotalWorldTime() + 40);
    }
    void tick(int state, int ticks) {
        if(!eligible(victim) || !boss.isOperational() || !GeminiCaptures.valid(boss, victim)) { interrupt("Invalid victim or captor"); return; }
        if(victim instanceof EntityPlayer && victim.isSneaking()) sneakTicks++; else sneakTicks = 0;
        if(sneakTicks >= 12) { interrupt("Victim escaped"); return; }
        tag();
        Vec3 anchor = boss.pose(1).captureAnchor();
        double x = anchor.xCoord, y = anchor.yCoord - victim.height * 0.5 + (victim.posY - victim.boundingBox.minY), z = anchor.zCoord;
        double dx = x - victim.posX, dy = y - victim.posY, dz = z - victim.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // A hand pose can change on a state boundary. The grip already validates external teleports.
        double step = distance > 0.55D ? 0.55D / distance : 1D;
        dx *= step; dy *= step; dz *= step;
        AxisAlignedBB at = victim.boundingBox.getOffsetBoundingBox(dx, dy, dz);
        AxisAlignedBB swept = victim.boundingBox.addCoord(dx, dy, dz).contract(0.001, 0.001, 0.001);
        if(!GardenerNests.loaded(boss.worldObj, swept) || !boss.worldObj.func_147461_a(swept).isEmpty()
                || boss.worldObj.isAnyLiquid(at)) { interrupt("Hand path obstructed"); return; }
        GeminiCaptures.position(victim, victim.posX + dx, victim.posY + dy, victim.posZ + dz);
        if(state == EntityWretch.HOLD && ticks >= 36) {
            if(GeminiConfig.wretchAssimilation) {
                GeminiCaptures.diagnostic(boss, victim, "pull", "Drawing prey into torso", ticks, anchor);
                boss.begin(EntityWretch.PULL);
            }
            else boss.begin(EntityWretch.THROW);
        } else if(state == EntityWretch.PULL && ticks >= 60) {
            GeminiCaptures.diagnostic(boss, victim, "assimilate", "Finished pull", ticks, anchor);
            boss.begin(GeminiConfig.wretchAssimilation ? EntityWretch.ASSIMILATE : EntityWretch.THROW); boss.voice("assimilate", 1.3F);
        } else if(state == EntityWretch.ASSIMILATE) {
            if(!GeminiConfig.wretchAssimilation) { interrupt("Assimilation disabled"); return; }
            if(ticks % 12 == 0) ((WorldServer) boss.worldObj).func_147487_a("reddust", x, y + victim.height * 0.5, z, 3, .22, .3, .22, 0);
            // Roots close around the living victim once. Ordinary swipes/slams are never infectious.
            if(ticks >= 40 && !exposure) {
                exposure = true;
                float chance = victim instanceof EntityPlayer ? GeminiConfig.deepRootPlayerTransmissionChance : GeminiConfig.deepRootMobTransmissionChance;
                if(boss.getRNG().nextFloat() < chance) GeminiInfections.exposeContact(victim, "wretch-roots:" + boss.getUniqueID(), "wretch");
            }
            if(ticks >= 100) complete();
        } else if(state == EntityWretch.THROW && ticks >= 16) {
            EntityLivingBase thrown = victim;
            boss.recovery();
            if(thrown.attackEntityFrom(DamageSource.causeMobDamage(boss), 6F) && thrown.isEntityAlive()) {
                double tx = thrown.posX - boss.posX, tz = thrown.posZ - boss.posZ, length = Math.max(.1, Math.sqrt(tx * tx + tz * tz));
                thrown.addVelocity(tx / length * 1.25, .65, tz / length * 1.25); thrown.velocityChanged = true;
            }
        }
    }
    private void complete() {
        EntityLivingBase absorbed = victim;
        completing = true; attemptedDeath = null;
        boolean hit, confirmed;
        try {
            hit = absorbed.attackEntityFrom(DamageSource.causeMobDamage(boss), Math.max(40F, absorbed.getHealth() * 10F + 100F));
            // Read cancellation after every Forge listener, including later listeners at LOWEST.
            confirmed = attemptedDeath != null && !attemptedDeath.isCanceled();
        } finally { completing = false; attemptedDeath = null; }
        if(hit && confirmed && !absorbed.isEntityAlive() && boss.anatomy.incorporate(absorbed)) boss.syncAnatomy();
        GeminiCaptures.diagnostic(boss, absorbed, "assimilate", confirmed ? "Assimilation completed" : "Assimilation damage or death vetoed",
                100, boss.pose(1).captureAnchor());
        // A canceled death/damage or immunity leaves the live entity intact. No retry loop or manual removal.
        boss.recovery();
    }
    void death(LivingDeathEvent event) { if(completing) attemptedDeath = event; else boss.recovery(); }
    private void interrupt(String reason) {
        GeminiCaptures.diagnostic(boss, victim, Integer.toString(boss.state()), reason, sneakTicks, null);
        boss.recovery();
    }
    void release() {
        EntityLivingBase released = victim; victim = null; attemptedDeath = null; sneakTicks = 0; boss.captiveId(-1);
        if(released == null) return;
        GeminiCaptures.diagnostic(boss, released, Integer.toString(boss.state()), "Grip released", 0, null);
        GeminiCaptures.release(boss, released);
        NBTTagCompound tag = released.getEntityData();
        if(boss.getUniqueID().toString().equals(tag.getString("WretchCaptor"))) {
            tag.removeTag("WretchCaptor"); tag.removeTag("WretchCaptorId"); tag.removeTag("WretchCaptorUntil");
        }
        tag.setLong("WretchEscapeUntil", released.worldObj.getTotalWorldTime() + 200);
        if(released.isEntityAlive()) {
            tag.setLong("WretchSafeFallUntil", released.worldObj.getTotalWorldTime() + 200); released.fallDistance = 0;
            released.velocityChanged = true; boss.voice("stretch", 1F);
            if(released instanceof EntityPlayer) ((EntityPlayer) released).addChatMessage(new ChatComponentTranslation("wretch.capture.released"));
        }
    }
}
