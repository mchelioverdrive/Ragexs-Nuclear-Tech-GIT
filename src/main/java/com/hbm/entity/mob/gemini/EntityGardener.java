package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import com.hbm.world.gemini.MockerySpawning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A nest-bound miniboss. Captives stay real entities and are never mounted, teleported or AI-disabled. */
public final class EntityGardener extends EntityLiving implements IMob, IBossDisplayData, IGeminiCreature {
    public static final int IDLE = 0, REACH = 1, HOLD = 2, SLAM = 3;
    private final EntityGardenerTentacle[] parts = new EntityGardenerTentacle[GardenerPose.ARMS * GardenerPose.SEGMENTS];
    private final float[] limbHealth = {32, 32, 32, 32, 32, 32};
    private final int[] recovery = new int[6], limbHurt = new int[6];
    private final Set<UUID> struck = new HashSet<UUID>();
    private EntityLivingBase captive;
    private double nestX, nestY, nestZ;
    private boolean anchored, fromSave;
    private int cooldown = 60, attackTicks, summons, summonTicks;
    private int smashCursor = -1, smashX, smashY, smashZ, smashRemoved;

    public EntityGardener(World world) {
        super(world); setSize(3.4F, 3.5F); experienceValue = 80; func_110163_bv();
        ignoreFrustumCheck = true; setHealth(getMaxHealth());
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, -1); // Eye target
        dataWatcher.addObject(21, (byte) IDLE);
        dataWatcher.addObject(22, 0); // State start in world ticks, synchronized without a packet per frame
        dataWatcher.addObject(23, (byte) 0);
        dataWatcher.addObject(24, 0F); dataWatcher.addObject(25, 0F); dataWatcher.addObject(26, 0F);
        dataWatcher.addObject(27, (byte) 0); // Disabled limb mask
        dataWatcher.addObject(28, (byte) 0); // Server-owned activity, also freezes dormant presentation
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(360D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(18D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }
    @Override protected void updateEntityActionState() { }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public void applyEntityCollision(Entity other) { }
    @Override public void moveEntity(double x, double y, double z) { }
    @Override protected void fall(float distance) { }
    @Override protected String getLivingSound() { return null; }
    @Override protected String getHurtSound() { return "mob.slime.big"; }
    @Override protected String getDeathSound() { return "mob.ghast.death"; }
    public boolean wasLoaded() { return fromSave; }
    public boolean isAwake() { return dataWatcher.getWatchableObjectByte(28) != 0; }
    public void setNestIdentity(UUID id) { entityUniqueID = id; }
    public int state() { return dataWatcher.getWatchableObjectByte(21); }
    public int attackArm() { return dataWatcher.getWatchableObjectByte(23); }
    public float stateTime(float partial) { return Math.max(0, (int) worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(22) + partial); }
    public float aimX() { return dataWatcher.getWatchableObjectFloat(24); }
    public float aimY() { return dataWatcher.getWatchableObjectFloat(25); }
    public float aimZ() { return dataWatcher.getWatchableObjectFloat(26); }
    public boolean armDisabled(int arm) { return arm < 0 || arm >= 6 || (dataWatcher.getWatchableObjectByte(27) & (1 << arm)) != 0; }
    public EntityLivingBase eyeTarget() {
        Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
        return entity instanceof EntityLivingBase && entity.isEntityAlive() ? (EntityLivingBase) entity : null;
    }
    public void anchor(double x, double y, double z) {
        nestX = x; nestY = y; nestZ = z; anchored = true;
        setPosition(x, y, z);
    }
    private boolean active() {
        return isEntityAlive() && anchored && nestY >= 8 && nestY <= 44 && Math.abs(posY - nestY) < 1D
                && GardenerNests.active(worldObj) && GardenerNests.loaded(worldObj, boundingBox.expand(10, 0, 10))
                && !worldObj.canBlockSeeTheSky(MathHelper.floor_double(nestX), MathHelper.floor_double(nestY + 5), MathHelper.floor_double(nestZ));
    }
    private boolean prey(EntityLivingBase target) {
        if(target == null || target == this || target instanceof IGeminiCreature || target instanceof IBossDisplayData
                || !target.isEntityAlive() || target.worldObj != worldObj || !target.addedToChunk
                || target.width > 2.2F || target.height > 3F || target.ridingEntity != null || target.riddenByEntity != null
                || target.getEntityData().hasKey("MockeryCaptor")
                || target.getEntityData().getLong("GardenerEscapeUntil") > worldObj.getTotalWorldTime()) return false;
        if(target instanceof EntityPlayer && (((EntityPlayer) target).capabilities.disableDamage
                || ((EntityPlayer) target).capabilities.isCreativeMode)) return false;
        return Math.abs(target.posY - nestY) < 6D && getDistanceSqToEntity(target) <= 18D * 18D;
    }
    private boolean reach(EntityLivingBase target, double range) {
        return prey(target) && getDistanceSqToEntity(target) <= range * range
                && GardenerNests.loaded(worldObj, boundingBox.addCoord(target.posX - posX, target.posY - posY, target.posZ - posZ).expand(1, 1, 1))
                && getEntitySenses().canSee(target)
                && worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + 1.8D, posZ),
                    Vec3.createVectorHelper(target.posX, target.boundingBox.minY + target.height * 0.5D, target.posZ), false, true, false) == null;
    }
    private void findTarget() {
        EntityLivingBase closest = null;
        double best = Double.MAX_VALUE;
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.expand(18, 6, 18))) {
            if(++examined > 32) break;
            EntityLivingBase target = (EntityLivingBase) entry;
            if(!reach(target, target instanceof EntityPlayer ? 18D : 8D)) continue;
            double score = getDistanceSqToEntity(target) + (target instanceof EntityPlayer ? 0 : 400D);
            if(score < best) { best = score; closest = target; }
        }
        setAttackTarget(closest);
        dataWatcher.updateObject(20, closest == null ? -1 : closest.getEntityId());
        if(closest != null) com.hbm.world.gemini.GeminiPopulation.alert(this, closest);
    }
    private void aim(EntityLivingBase target, boolean ground) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        double scale = length > 7.5D ? 7.5D / length : 1D;
        dataWatcher.updateObject(24, (float) (dx * scale));
        dataWatcher.updateObject(26, (float) (dz * scale));
        double y = target.boundingBox.minY + target.height * 0.5D - posY;
        if(ground) {
            int x = MathHelper.floor_double(posX + dx * scale), z = MathHelper.floor_double(posZ + dz * scale);
            y = 0.3D;
            for(int dy = 3; dy >= -2; dy--) if(worldObj.getBlock(x, MathHelper.floor_double(posY) + dy - 1, z).getMaterial().blocksMovement()) { y = dy + 0.25D; break; }
        }
        dataWatcher.updateObject(25, (float) MathHelper.clamp_double(y, 0.3D, 4D));
    }
    private void begin(int next) {
        dataWatcher.updateObject(21, (byte) next);
        dataWatcher.updateObject(22, (int) worldObj.getTotalWorldTime());
        attackTicks = 0; struck.clear();
    }
    private void release() {
        if(captive != null) {
            captive.getEntityData().setLong("GardenerEscapeUntil", worldObj.getTotalWorldTime() + 400L);
            captive.velocityChanged = true;
        }
        captive = null;
        if(state() != IDLE) { begin(IDLE); cooldown = 45; }
    }

    @Override public void onUpdate() {
        motionX = motionY = motionZ = 0;
        super.onUpdate();
        if(worldObj.isRemote) return;
        if(!anchored) anchor(posX, posY, posZ);
        setPosition(nestX, nestY, nestZ);
        if(!active()) {
            release(); setAttackTarget(null); dataWatcher.updateObject(20, -1); dataWatcher.updateObject(28, (byte) 0);
            smashCursor = -1; removeParts(); return;
        }
        dataWatcher.updateObject(28, (byte) 1);
        getEntitySenses().clearSensingCache();
        int mask = 0;
        for(int arm = 0; arm < 6; arm++) {
            if(limbHurt[arm] > 0) limbHurt[arm]--;
            if(recovery[arm] > 0 && --recovery[arm] == 0) limbHealth[arm] = 32F;
            if(recovery[arm] > 0) mask |= 1 << arm;
        }
        dataWatcher.updateObject(27, (byte) mask);
        if(ticksExisted % 20 == 1) { maintainParts(); if(captive == null) findTarget(); }
        EntityLivingBase target = getAttackTarget();
        if(!prey(target)) { setAttackTarget(null); dataWatcher.updateObject(20, -1); target = null; }
        if(cooldown > 0) cooldown--;
        if(state() == IDLE && target != null && cooldown == 0 && reach(target, 9D)) {
            int arm = rand.nextInt(6);
            for(int i = 0; i < 6 && armDisabled(arm); i++) arm = (arm + 1) % 6;
            if(!armDisabled(arm)) {
                dataWatcher.updateObject(23, (byte) arm);
                boolean slam = rand.nextInt(getHealth() < getMaxHealth() * 0.4F ? 2 : 3) == 0;
                aim(target, slam); begin(slam ? SLAM : REACH);
                playSound("mob.ghast.warn", 0.65F, 0.5F);
            }
        }
        if(state() != IDLE) tickAttack();
        if(target != null && ++summonTicks >= (getHealth() < getMaxHealth() * 0.5F ? 200 : 400)) {
            summonTicks = 0; summon();
        }
        for(EntityGardenerTentacle part : parts) if(part != null && !part.isDead) part.follow();
        tickSmash();
    }
    private void tickAttack() {
        attackTicks++;
        if(armDisabled(attackArm())) { release(); return; }
        EntityLivingBase target = getAttackTarget();
        if(state() == HOLD) {
            if(captive == null || !reach(captive, 9D) || attackTicks >= 80) { release(); return; }
            aim(captive, false);
            // Native movement resolves collisions on the target's owning tick. Never teleport through terrain.
            double dx = posX - captive.posX, dz = posZ - captive.posZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if(distance > 2.8D) {
                captive.motionX = dx / Math.max(1D, distance) * 0.24D;
                captive.motionZ = dz / Math.max(1D, distance) * 0.24D;
                captive.motionY = MathHelper.clamp_double(captive.motionY, -0.1D, 0.08D);
                captive.velocityChanged = true;
            }
            if(attackTicks % 20 == 0 && distance < 4D) wound(captive, 4F);
            return;
        }
        if(state() == REACH && attackTicks <= 18 && reach(target, 9D)) aim(target, false);
        if(state() == REACH && attackTicks >= 24 && attackTicks <= 34) {
            Vec3 a = GardenerPose.point(this, attackArm(), 3, 1), b = GardenerPose.point(this, attackArm(), 4, 1);
            for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, GardenerPose.bounds(a, b, GardenerPose.RADIUS))) {
                EntityLivingBase victim = (EntityLivingBase) entry;
                if(!reach(victim, 9D) || !GardenerPose.touches(a, b, victim.boundingBox, GardenerPose.RADIUS)) continue;
                captive = victim; setAttackTarget(victim); dataWatcher.updateObject(20, victim.getEntityId());
                begin(HOLD);
                GeminiInfections.expose(victim, "gardener_capture:" + getUniqueID(), "gardener");
                return;
            }
        }
        if(state() == SLAM && attackTicks >= 23 && attackTicks <= 28) {
            for(int segment = 0; segment < GardenerPose.SEGMENTS; segment++) {
                Vec3 a = GardenerPose.point(this, attackArm(), segment, 1), b = GardenerPose.point(this, attackArm(), segment + 1, 1);
                for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, GardenerPose.bounds(a, b, GardenerPose.RADIUS))) {
                    EntityLivingBase victim = (EntityLivingBase) entry;
                    if(prey(victim) && !struck.contains(victim.getUniqueID()) && reach(victim, 10D)
                            && GardenerPose.touches(a, b, victim.boundingBox, GardenerPose.RADIUS)) {
                        struck.add(victim.getUniqueID()); wound(victim, getHealth() < getMaxHealth() * 0.4F ? 12F : 9F);
                    }
                }
            }
            if(attackTicks == 26) {
                Vec3 tip = GardenerPose.point(this, attackArm(), 4, 1);
                smashX = MathHelper.floor_double(tip.xCoord); smashY = MathHelper.floor_double(tip.yCoord); smashZ = MathHelper.floor_double(tip.zCoord);
                smashCursor = 0; smashRemoved = 0; playSound("mob.irongolem.throw", 1.5F, 0.55F);
            }
        }
        if(attackTicks >= 44) { begin(IDLE); cooldown = getHealth() < getMaxHealth() * 0.4F ? 20 : 55; }
    }
    private void wound(EntityLivingBase victim, float damage) {
        // Limb contacts are already validated above. Use explicit exposure to avoid inventing body melee contact.
        float health = victim.getHealth();
        if(victim.attackEntityFrom(DamageSource.causeMobDamage(this), damage)
                && victim.isEntityAlive() && victim.getHealth() < health)
            GeminiInfections.expose(victim, "gardener_wound:" + getUniqueID(), "gardener");
    }
    private void summon() {
        if(summons >= GeminiConfig.gardenerSupportLimit || !worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                || !MockerySpawning.canSpawn(worldObj, null)) return;
        double angle = rand.nextDouble() * Math.PI * 2D;
        EntityNaturesMockery support = new EntityNaturesMockery(worldObj);
        support.setLocationAndAngles(nestX + Math.cos(angle) * 5D, nestY, nestZ + Math.sin(angle) * 5D, rand.nextFloat() * 360F, 0);
        support.seedGardenerColony(nestX, nestY, nestZ);
        if(MockerySpawning.space(support, null) && worldObj.spawnEntityInWorld(support)) summons++;
    }
    private void tickSmash() {
        for(int i = 0; i < 4 && smashCursor >= 0 && smashCursor < 75; i++) {
            int cursor = smashCursor++;
            int x = smashX + cursor % 5 - 2, z = smashZ + cursor / 5 % 5 - 2, y = smashY + cursor / 25 - 1;
            if(smashRemoved < GeminiConfig.gardenerBlocksPerSlam && Math.abs(x + 0.5D - nestX) <= 9D
                    && Math.abs(z + 0.5D - nestZ) <= 9D && y >= nestY - 2 && y <= nestY + 5
                    && GardenerNests.change(worldObj, this, x, y, z, net.minecraft.init.Blocks.air)) smashRemoved++;
        }
        if(smashCursor >= 75) smashCursor = -1;
    }
    public AxisAlignedBB segmentBox(int slot) {
        int arm = MathHelper.clamp_int(slot / GardenerPose.SEGMENTS, 0, 5), segment = MathHelper.clamp_int(slot % GardenerPose.SEGMENTS, 0, 3);
        return GardenerPose.bounds(GardenerPose.point(this, arm, segment, 1), GardenerPose.point(this, arm, segment + 1, 1), GardenerPose.RADIUS);
    }
    public boolean hitTentacle(int slot, DamageSource source, float damage) {
        int arm = slot / GardenerPose.SEGMENTS;
        if(worldObj.isRemote || !active() || armDisabled(arm) || limbHurt[arm] > 0 || damage <= 0 || Float.isNaN(damage)
                || source.getEntity() instanceof IGeminiCreature) return false;
        // Players select broad-phase part AABBs. Confirm their actual attack ray intersects the capsule.
        Vec3 a = GardenerPose.point(this, arm, slot % 4, 1), b = GardenerPose.point(this, arm, slot % 4 + 1, 1);
        if(source.isProjectile() && source.getSourceOfDamage() != null) {
            Entity projectile = source.getSourceOfDamage();
            Vec3 from = Vec3.createVectorHelper(projectile.posX, projectile.posY, projectile.posZ);
            Vec3 to = from.addVector(projectile.motionX, projectile.motionY, projectile.motionZ);
            if(!GardenerPose.rayTouches(a, b, from, to, GardenerPose.RADIUS + projectile.width * 0.5D)) return false;
        } else if(source.getEntity() instanceof EntityPlayer && !source.isExplosion()) {
            EntityPlayer player = (EntityPlayer) source.getEntity();
            Vec3 eye = player.getPosition(1).addVector(0, player.getEyeHeight(), 0), look = player.getLookVec();
            double range = player.capabilities.isCreativeMode ? 6D : 4.5D;
            Vec3 end = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
            if(!GardenerNests.loaded(worldObj, GardenerPose.bounds(eye, end, 1D))
                    || !GardenerPose.rayTouches(a, b, eye, end, GardenerPose.RADIUS)) return false;
            net.minecraft.util.MovingObjectPosition obstruction = worldObj.func_147447_a(eye, end, false, true, false);
            if(obstruction != null && !GardenerPose.rayTouches(a, b, eye, obstruction.hitVec, GardenerPose.RADIUS)) return false;
        }
        if(isEntityInvulnerable() || net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.living.LivingAttackEvent(this, source, damage))) return false;
        limbHealth[arm] -= Math.min(1000F, damage); limbHurt[arm] = 10;
        playSound("mob.slime.big", 0.8F, 0.6F);
        if(limbHealth[arm] <= 0) {
            recovery[arm] = 300;
            dataWatcher.updateObject(27, (byte) (dataWatcher.getWatchableObjectByte(27) | 1 << arm));
            if(attackArm() == arm) release();
        }
        return true;
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        return active() && !(source.getEntity() instanceof IGeminiCreature) && super.attackEntityFrom(source, damage);
    }
    private void maintainParts() {
        for(int i = 0; i < parts.length; i++) if(parts[i] == null || parts[i].isDead || !parts[i].addedToChunk) {
            if(parts[i] != null) parts[i].setDead();
            EntityGardenerTentacle part = new EntityGardenerTentacle(worldObj); part.attach(this, i);
            if(GardenerNests.loaded(worldObj, part.boundingBox.expand(1, 0, 1)) && worldObj.spawnEntityInWorld(part)) parts[i] = part;
        }
    }
    private void removeParts() { for(int i = 0; i < parts.length; i++) if(parts[i] != null) { parts[i].setDead(); parts[i] = null; } }
    @Override public void onDeath(DamageSource source) { if(!worldObj.isRemote) { release(); removeParts(); } super.onDeath(source); }
    @Override public void setDead() { if(!worldObj.isRemote) { release(); removeParts(); } super.setDead(); }
    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("NestAnchored", anchored); nbt.setDouble("NestX", nestX); nbt.setDouble("NestY", nestY); nbt.setDouble("NestZ", nestZ);
        nbt.setInteger("Summons", summons); nbt.setInteger("SummonTicks", summonTicks); nbt.setInteger("Cooldown", cooldown);
        for(int i = 0; i < 6; i++) { nbt.setFloat("LimbHealth" + i, limbHealth[i]); nbt.setInteger("LimbRecovery" + i, recovery[i]); }
        // Captures and in-flight attacks deliberately end on reload. No restraint is stored on the victim.
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt); fromSave = true;
        anchored = nbt.getBoolean("NestAnchored"); nestX = nbt.getDouble("NestX"); nestY = nbt.getDouble("NestY"); nestZ = nbt.getDouble("NestZ");
        summons = MathHelper.clamp_int(nbt.getInteger("Summons"), 0, 16); summonTicks = MathHelper.clamp_int(nbt.getInteger("SummonTicks"), 0, 400);
        cooldown = Math.max(80, MathHelper.clamp_int(nbt.getInteger("Cooldown"), 0, 400));
        for(int i = 0; i < 6; i++) {
            limbHealth[i] = nbt.hasKey("LimbHealth" + i) ? MathHelper.clamp_float(nbt.getFloat("LimbHealth" + i), 0, 32) : 32;
            recovery[i] = MathHelper.clamp_int(nbt.getInteger("LimbRecovery" + i), 0, 300);
            if(limbHealth[i] <= 0 && recovery[i] == 0) recovery[i] = 300;
        }
        captive = null; begin(IDLE);
    }
}
