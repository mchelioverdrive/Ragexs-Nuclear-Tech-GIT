package com.hbm.entity.mob.gemini;

import java.lang.reflect.Field;
import cpw.mods.fml.relauncher.ReflectionHelper;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
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
                || entity.getEntityData().hasKey("MockeryCaptor") || !boss.inTerritory(entity.posX, entity.posZ, boss.territoryRadius())
                || boss.worldObj.getEntityByID(entity.getEntityId()) != entity) return false;
        NBTTagCompound tag = entity.getEntityData();
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
        victim = entity; sneakTicks = 0; exposure = false; tag(); boss.captiveId(entity.getEntityId()); boss.begin(EntityWretch.HOLD);
        boss.voice("grab", 1.2F);
        if(entity instanceof EntityPlayer) ((EntityPlayer) entity).addChatMessage(new ChatComponentTranslation("wretch.capture.escape"));
        return true;
    }
    private void tag() {
        NBTTagCompound tag = victim.getEntityData(); tag.setString("WretchCaptor", boss.getUniqueID().toString());
        tag.setInteger("WretchCaptorId", boss.getEntityId()); tag.setLong("WretchCaptorUntil", boss.worldObj.getTotalWorldTime() + 40);
    }
    void tick(int state, int ticks) {
        if(!eligible(victim) || !boss.isOperational()) { boss.recovery(); return; }
        if(victim instanceof EntityPlayer && victim.isSneaking()) sneakTicks++; else sneakTicks = 0;
        if(sneakTicks >= 12) { boss.recovery(); return; }
        tag();
        Vec3 anchor = boss.pose(1).captureAnchor();
        double x = anchor.xCoord, y = anchor.yCoord - victim.height * 0.5 + (victim.posY - victim.boundingBox.minY), z = anchor.zCoord;
        double dx = x - victim.posX, dy = y - victim.posY, dz = z - victim.posZ;
        // A dimension change, teleport, intervening block or blocked pull releases the actual victim.
        if(dx * dx + dy * dy + dz * dz > 6.25) { boss.recovery(); return; }
        AxisAlignedBB at = victim.boundingBox.getOffsetBoundingBox(dx, dy, dz);
        AxisAlignedBB swept = victim.boundingBox.addCoord(dx, dy, dz);
        if(!GardenerNests.loaded(boss.worldObj, swept) || !boss.worldObj.func_147461_a(swept).isEmpty()
                || boss.worldObj.isAnyLiquid(at)) { boss.recovery(); return; }
        victim.motionX = victim.motionY = victim.motionZ = 0; victim.fallDistance = 0;
        victim.moveEntity(dx, dy, dz);
        if(victim instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) victim;
            // Vanilla recognizes block support only; a real, collision-checked hand attachment
            // must not accumulate floating kicks. Position correction alone does not reset it.
            HeldPlayer.support(player);
            if(boss.ticksExisted % 2 == 0) player.playerNetServerHandler.setPlayerLocation(victim.posX, victim.posY, victim.posZ, victim.rotationYaw, victim.rotationPitch);
        }
        victim.velocityChanged = true;
        if(state == EntityWretch.HOLD && ticks >= 36) {
            if(GeminiConfig.wretchAssimilation) boss.begin(EntityWretch.PULL);
            else boss.begin(EntityWretch.THROW);
        } else if(state == EntityWretch.PULL && ticks >= 60) {
            boss.begin(GeminiConfig.wretchAssimilation ? EntityWretch.ASSIMILATE : EntityWretch.THROW); boss.voice("assimilate", 1.3F);
        } else if(state == EntityWretch.ASSIMILATE) {
            if(!GeminiConfig.wretchAssimilation) { boss.recovery(); return; }
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
            if(thrown.attackEntityFrom(DamageSource.causeMobDamage(boss), 6F) && thrown.isEntityAlive()) {
                double tx = thrown.posX - boss.posX, tz = thrown.posZ - boss.posZ, length = Math.max(.1, Math.sqrt(tx * tx + tz * tz));
                thrown.addVelocity(tx / length * 1.25, .65, tz / length * 1.25); thrown.velocityChanged = true;
            }
            boss.recovery();
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
        // A canceled death/damage or immunity leaves the live entity intact. No retry loop or manual removal.
        boss.recovery();
    }
    void death(LivingDeathEvent event) { if(completing) attemptedDeath = event; else boss.recovery(); }
    private static final class HeldPlayer {
        private static final Field FLOATING = ReflectionHelper.findField(NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f");
        static void support(EntityPlayerMP player) {
            if(player.playerNetServerHandler == null) return;
            try { FLOATING.setInt(player.playerNetServerHandler, 0); }
            catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot validate Wretch hand support", ex); }
        }
    }
    void release() {
        EntityLivingBase released = victim; victim = null; attemptedDeath = null; sneakTicks = 0; boss.captiveId(-1);
        if(released == null) return;
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
