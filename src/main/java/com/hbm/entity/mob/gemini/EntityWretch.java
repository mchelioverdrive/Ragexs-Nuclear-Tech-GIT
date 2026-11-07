package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.WretchCamps;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Territory-bound major boss. The server owns every attack, capture, throw and terrain mutation. */
public final class EntityWretch extends EntityCreature implements IMob, IBossDisplayData, IGeminiCreature {
    public static final int IDLE = 0, SWIPE = 1, SLAM = 2, GRAB = 3, HOLD = 4, THROW = 5, RECOVER = 6, FEED = 7;
    private final EntityWretchPart[] parts = new EntityWretchPart[5]; // two segments per arm, head
    private final Set<UUID> struck = new HashSet<UUID>();
    private int homeX, homeY, homeZ;
    private boolean campBound, fromSave;
    private int attackTicks, cooldown = 80, feedCooldown = 300, escapeTicks, lastAttack = SWIPE;
    private float interruptDamage;
    private EntityPlayer captive;
    private EntityLivingBase meal;
    private int smashCursor = -1, smashX, smashY, smashZ, smashRemoved;

    public EntityWretch(World world) {
        super(world); setSize(2.8F, 3.7F); stepHeight = 1.1F;
        experienceValue = 250; func_110163_bv(); ignoreFrustumCheck = true;
        getNavigator().setAvoidsWater(true); setHealth(getMaxHealth());
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(20, (byte) IDLE); dataWatcher.addObject(21, 0);
        dataWatcher.addObject(22, (byte) 0); dataWatcher.addObject(23, -1);
        dataWatcher.addObject(24, 0F); dataWatcher.addObject(25, 1F); dataWatcher.addObject(26, 4F);
        dataWatcher.addObject(27, (byte) 0); dataWatcher.addObject(28, (byte) 0);
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(GeminiConfig.wretchHealth);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.21D);
        // Native pathfinding allocates a ChunkCache with followRange + 8 blocks. Detection is separately 32.
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(16D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(0.95D);
    }
    @Override public boolean isAIEnabled() { return true; }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override protected String getLivingSound() { return isAwake() ? "mob.endermen.stare" : null; }
    @Override protected String getHurtSound() { return "mob.irongolem.hit"; }
    @Override protected String getDeathSound() { return "mob.ghast.death"; }
    @Override protected float getSoundPitch() { return 0.55F; }
    public boolean wasLoaded() { return fromSave; }
    public boolean hasCamp() { return campBound; }
    public int campX() { return homeX; }
    public int campY() { return homeY; }
    public int campZ() { return homeZ; }
    public void bindCamp(UUID id, int x, int y, int z) { entityUniqueID = id; homeX = x; homeY = y; homeZ = z; campBound = true; }
    public boolean inTerritory(double x, double z, double radius) {
        double dx = x - homeX - 0.5D, dz = z - homeZ - 0.5D;
        return campBound && dx * dx + dz * dz <= radius * radius;
    }
    public int state() { return dataWatcher.getWatchableObjectByte(20); }
    public float stateTime(float partial) { return Math.max(0, (int) worldObj.getTotalWorldTime() - dataWatcher.getWatchableObjectInt(21) + partial); }
    public boolean isAwake() { return dataWatcher.getWatchableObjectByte(22) != 0; }
    public float aimX() { return dataWatcher.getWatchableObjectFloat(24); }
    public float aimY() { return dataWatcher.getWatchableObjectFloat(25); }
    public float aimZ() { return dataWatcher.getWatchableObjectFloat(26); }
    public int attackArm() { return dataWatcher.getWatchableObjectByte(27); }
    public int phase() { return dataWatcher.getWatchableObjectByte(28); }
    private boolean active() {
        return isEntityAlive() && WretchCamps.active(worldObj) && WretchCamps.owns(this)
                && GardenerNests.loaded(worldObj, boundingBox.expand(9, 1, 9));
    }
    private void stop() {
        getNavigator().clearPathEntity(); getMoveHelper().setMoveTo(posX, posY, posZ, 0D);
        moveForward = moveStrafing = 0; motionX = motionZ = 0; setAIMoveSpeed(0);
    }
    @Override public void moveEntity(double x, double y, double z) {
        if(!worldObj.isRemote && (!isAwake() || !GardenerNests.loaded(worldObj, boundingBox.addCoord(x, y, z).expand(2, 0, 2)))) {
            motionX = motionZ = 0; return;
        }
        super.moveEntity(x, y, z);
    }
    @Override protected void updateAITasks() {
        if(isAwake()) super.updateAITasks(); else stop();
    }
    @Override public void onUpdate() {
        if(!worldObj.isRemote) {
            boolean awake = active();
            dataWatcher.updateObject(22, (byte) (awake ? 1 : 0));
            if(!awake) {
                release(); if(state() != IDLE) begin(IDLE);
                stop(); motionY = 0; smashCursor = -1; removeParts(); setAttackTarget(null);
            }
        }
        super.onUpdate();
        if(worldObj.isRemote || !isAwake()) return;
        if(ticksExisted % 10 == 1) maintainParts();
        for(EntityWretchPart part : parts) if(part != null && !part.isDead) part.follow();
        tickSmash();
        renderYawOffset = rotationYaw;
    }
    private boolean player(EntityLivingBase entity) {
        if(!(entity instanceof EntityPlayer) || !entity.isEntityAlive() || entity.worldObj != worldObj || !entity.addedToChunk
                || entity.ridingEntity != null || entity.riddenByEntity != null || entity.getEntityData().hasKey("MockeryCaptor")) return false;
        EntityPlayer p = (EntityPlayer) entity;
        return !p.capabilities.disableDamage && !p.capabilities.isCreativeMode && inTerritory(p.posX, p.posZ, 32)
                && Math.abs(p.posY - posY) <= 12D;
    }
    private boolean visible(EntityLivingBase target, double range) {
        if(target == null || target.worldObj != worldObj || !target.isEntityAlive() || getDistanceSqToEntity(target) > range * range
                || !GardenerNests.loaded(worldObj, boundingBox.addCoord(target.posX - posX, target.posY - posY, target.posZ - posZ).expand(1, 0, 1))) return false;
        return worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + 2D, posZ),
                Vec3.createVectorHelper(target.posX, target.boundingBox.minY + target.height * 0.5D, target.posZ), false, true, false) == null;
    }
    private void chooseTarget() {
        EntityLivingBase nearest = null; double best = 32D * 32D;
        for(Object entry : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) entry; double distance = getDistanceSqToEntity(p);
            if(player(p) && distance < best && visible(p, 32)) { best = distance; nearest = p; }
        }
        setAttackTarget(nearest);
        if(nearest != null && ticksExisted % 20 == 1) com.hbm.world.gemini.GeminiPopulation.alert(this, nearest);
    }
    private void aim(EntityLivingBase target) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double distance = Math.sqrt(dx * dx + dz * dz), scale = distance > 5.7D ? 5.7D / distance : 1D;
        dataWatcher.updateObject(24, (float) (dx * scale)); dataWatcher.updateObject(26, (float) (dz * scale));
        dataWatcher.updateObject(25, (float) MathHelper.clamp_double(target.boundingBox.minY + target.height * 0.5D - posY, 0.5D, 4D));
    }
    private void begin(int next) {
        dataWatcher.updateObject(20, (byte) next); dataWatcher.updateObject(21, (int) worldObj.getTotalWorldTime());
        attackTicks = 0; struck.clear(); interruptDamage = 0;
    }
    private void recovery() { release(); meal = null; begin(RECOVER); stop(); }
    private void release() {
        if(captive != null) {
            captive.getEntityData().setLong("WretchEscapeUntil", worldObj.getTotalWorldTime() + 200L);
            captive.velocityChanged = true;
        }
        captive = null; escapeTicks = 0; dataWatcher.updateObject(23, -1);
    }
    @Override protected void updateAITick() {
        super.updateAITick();
        if(!isAwake()) return;
        int phase = getHealth() <= getMaxHealth() * 0.3F ? 2 : getHealth() <= getMaxHealth() * 0.65F ? 1 : 0;
        dataWatcher.updateObject(28, (byte) phase);
        if(cooldown > 0) cooldown--;
        if(feedCooldown > 0) feedCooldown--;
        if(ticksExisted % 10 == 1 && captive == null) chooseTarget();
        if(!player(getAttackTarget())) setAttackTarget(null);
        if(state() != IDLE) { stop(); tickAttack(); return; }
        EntityLivingBase target = getAttackTarget();
        if(cooldown == 0 && target != null && visible(target, 6.2D)) {
            faceEntity(target, 360, 20);
            aim(target); dataWatcher.updateObject(27, (byte) rand.nextInt(2));
            int next = rand.nextInt(phase >= 1 ? 3 : 5) == 0 ? SLAM : SWIPE;
            if(lastAttack != GRAB && target.getEntityData().getLong("WretchEscapeUntil") <= worldObj.getTotalWorldTime()
                    && rand.nextInt(phase == 2 ? 2 : 4) == 0) next = GRAB;
            if(next == SLAM && lastAttack == SLAM) next = SWIPE;
            lastAttack = next; begin(next); stop();
            playSound("mob.ghast.warn", 1.8F, next == SLAM ? 0.4F : 0.55F);
            return;
        }
        if(cooldown == 0 && feedCooldown == 0 && getHealth() < getMaxHealth() * 0.85F && findMeal()) {
            begin(FEED); stop(); feedCooldown = 600; return;
        }
        if(target != null) {
            if(ticksExisted % 15 == 0) walkToward(target.posX, target.posY, target.posZ, phase == 2 ? 1.12D : 1D);
            if(ticksExisted % 10 == 0 && getDistanceSqToEntity(target) > 9D) pursueBreak();
        } else if(ticksExisted % 80 == 0 || !inTerritory(posX, posZ, 26)) {
            double angle = rand.nextDouble() * Math.PI * 2D, radius = rand.nextDouble() * 18D;
            walkToward(homeX + 0.5D + Math.cos(angle) * radius, homeY, homeZ + 0.5D + Math.sin(angle) * radius, 0.65D);
        }
    }
    private void walkToward(double x, double y, double z, double speed) {
        // A short local leg with the full native path-search margin loaded. Never pathfind across an unloaded boundary.
        double dx = x - posX, dz = z - posZ, distance = Math.sqrt(dx * dx + dz * dz), scale = distance > 7D ? 7D / distance : 1D;
        double goalX = posX + dx * scale, goalZ = posZ + dz * scale;
        if(!inTerritory(goalX, goalZ, 28) || !GardenerNests.loaded(worldObj, boundingBox.expand(24, 0, 24))) { stop(); return; }
        getNavigator().tryMoveToXYZ(goalX, MathHelper.clamp_double(y, posY - 2, posY + 2), goalZ, speed);
    }
    private void tickAttack() {
        attackTicks++;
        int state = state();
        if(state == RECOVER) {
            if(attackTicks >= (phase() == 2 ? 25 : 40)) { begin(IDLE); cooldown = phase() == 2 ? 25 : 45; }
            return;
        }
        if(state == HOLD || state == THROW) {
            if(!player(captive) || !visible(captive, 7D)) { recovery(); return; }
            aim(captive);
            escapeTicks = captive.isSneaking() ? escapeTicks + 1 : 0;
            if(escapeTicks >= 12) { recovery(); return; }
            // Bounded pull through native movement; no teleport, mount, capability or persistent immobilization.
            double dx = posX - captive.posX, dz = posZ - captive.posZ, distance = Math.sqrt(dx * dx + dz * dz);
            if(distance > 3D && state == HOLD) {
                captive.motionX = dx / distance * 0.18D; captive.motionZ = dz / distance * 0.18D;
                captive.velocityChanged = true;
            }
            if(state == HOLD && attackTicks >= 30) begin(THROW);
            else if(state == THROW && attackTicks >= 16) {
                dx = captive.posX - posX; dz = captive.posZ - posZ; distance = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
                if(captive.attackEntityFrom(DamageSource.causeMobDamage(this), 6F)) {
                    captive.motionX = dx / distance * 1.25D; captive.motionY = 0.65D; captive.motionZ = dz / distance * 1.25D;
                    captive.velocityChanged = true; playSound("mob.irongolem.throw", 1.5F, 0.55F);
                }
                recovery();
            }
            return;
        }
        if(state == FEED) {
            if(!eligibleMeal(meal) || !visible(meal, 4.5D)) { recovery(); return; }
            aim(meal);
            if(attackTicks % 4 == 0) {
                double blend = attackTicks / 40D;
                ((WorldServer) worldObj).func_147487_a("reddust", meal.posX + (posX - meal.posX) * blend,
                        meal.posY + 0.5D + blend, meal.posZ + (posZ - meal.posZ) * blend, 5, 0.18D, 0.12D, 0.18D, 0D);
            }
            if(attackTicks == 40) {
                float health = meal.getHealth();
                if(health > 0 && meal.attackEntityFrom(DamageSource.causeMobDamage(this), health + 2F) && !meal.isEntityAlive()) {
                    heal(Math.min(30F, getMaxHealth() * 0.035F)); playSound("mob.slime.big", 1.2F, 0.5F);
                }
                recovery();
            }
            return;
        }
        if(state == SWIPE && attackTicks >= 23 && attackTicks <= 32) strike(false);
        if(state == GRAB && attackTicks >= 28 && attackTicks <= 35) {
            strike(true); if(state() == HOLD) return;
        }
        if(state == SLAM && attackTicks == 34) slam();
        if(attackTicks >= (state == SLAM ? 44 : 38)) recovery();
    }
    private void strike(boolean grab) {
        int arm = attackArm();
        Vec3 a = WretchPose.point(this, arm, 1, 1), b = WretchPose.point(this, arm, 2, 1);
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityPlayer.class, GardenerPose.bounds(a, b, WretchPose.ARM_RADIUS))) {
            EntityPlayer victim = (EntityPlayer) entry;
            if(!player(victim) || struck.contains(victim.getUniqueID()) || !visible(victim, 7D)
                    || !GardenerPose.touches(a, b, victim.boundingBox, WretchPose.ARM_RADIUS)) continue;
            struck.add(victim.getUniqueID());
            if(grab) {
                if(victim.getEntityData().getLong("WretchEscapeUntil") > worldObj.getTotalWorldTime()
                        || !victim.attackEntityFrom(DamageSource.causeMobDamage(this), 2F) || !victim.isEntityAlive()) continue;
                captive = victim; dataWatcher.updateObject(23, victim.getEntityId()); begin(HOLD);
                victim.addChatMessage(new ChatComponentTranslation("wretch.capture.escape"));
                return;
            }
            if(victim.attackEntityFrom(DamageSource.causeMobDamage(this), phase() == 2 ? 17F : 13F)) knock(victim, 0.95D, 0.3D);
        }
    }
    private void slam() {
        double x = posX + aimX(), z = posZ + aimZ();
        AxisAlignedBB area = AxisAlignedBB.getBoundingBox(x - 4, posY - 2, z - 4, x + 4, posY + 3, z + 4);
        if(!GardenerNests.loaded(worldObj, area.expand(2, 0, 2))) return;
        playSound("random.explode", 2F, 0.55F);
        ((WorldServer) worldObj).func_147487_a("blockcrack_" + net.minecraft.block.Block.getIdFromBlock(net.minecraft.init.Blocks.dirt) + "_0", x, posY + 0.1D, z, 36, 2D, 0.15D, 2D, 0.15D);
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityPlayer.class, area)) {
            EntityPlayer p = (EntityPlayer) entry;
            double dx = p.posX - x, dz = p.posZ - z;
            if(player(p) && dx * dx + dz * dz <= 16D && Math.abs(p.boundingBox.minY - posY) < 2D && visible(p, 10D)
                    && p.attackEntityFrom(DamageSource.causeMobDamage(this), phase() == 2 ? 21F : 17F)) knock(p, 0.8D, 0.65D);
        }
        smashX = MathHelper.floor_double(x); smashY = MathHelper.floor_double(posY); smashZ = MathHelper.floor_double(z);
        smashCursor = 0; smashRemoved = 0;
    }
    private void knock(EntityLivingBase victim, double force, double up) {
        double dx = victim.posX - posX, dz = victim.posZ - posZ, length = Math.max(0.1D, Math.sqrt(dx * dx + dz * dz));
        victim.addVelocity(dx / length * force, up, dz / length * force); victim.velocityChanged = true;
    }
    private boolean eligibleMeal(EntityLivingBase entity) {
        if(!(entity instanceof EntityAnimal) || entity instanceof IBossDisplayData || entity instanceof IGeminiCreature
                || !entity.isEntityAlive() || entity.worldObj != worldObj || !entity.addedToChunk || !inTerritory(entity.posX, entity.posZ, 28)
                || entity.ridingEntity != null || entity.riddenByEntity != null || entity.isEntityInvulnerable()
                || ((EntityLiving) entity).hasCustomNameTag() || ((EntityLiving) entity).getLeashed()
                || ((EntityAnimal) entity).isChild() || ((EntityAnimal) entity).isInLove()
                || entity instanceof EntityPig && ((EntityPig) entity).getSaddled()
                || entity.getEntityData().getBoolean("WretchProtected") || entity.getEntityData().hasKey("MockeryCaptor")) return false;
        Class<?> type = entity.getClass();
        // Exact vanilla types exclude optional mod bosses, machines, pets and inventory-bearing mounts.
        return type == EntityCow.class || type == EntityMooshroom.class || type == EntityPig.class
                || type == EntitySheep.class || type == EntityChicken.class;
    }
    private boolean findMeal() {
        int examined = 0;
        for(Object entry : worldObj.getEntitiesWithinAABB(EntityAnimal.class, boundingBox.expand(4, 2, 4))) {
            if(++examined > 32) break;
            EntityLivingBase candidate = (EntityLivingBase) entry;
            if(eligibleMeal(candidate) && visible(candidate, 4.5D)) { meal = candidate; aim(meal); return true; }
        }
        return false;
    }
    private void pursueBreak() {
        EntityLivingBase target = getAttackTarget();
        double dx = target.posX - posX, dz = target.posZ - posZ, length = Math.max(1D, Math.sqrt(dx * dx + dz * dz));
        int x = MathHelper.floor_double(posX + dx / length * 2.1D), z = MathHelper.floor_double(posZ + dz / length * 2.1D);
        // Four cells per half second; trunks are gradually torn apart without recursive tree traversal.
        for(int dy = 0; dy < 4; dy++) WretchCamps.breakNatural(this, x, MathHelper.floor_double(posY) + dy, z);
    }
    private void tickSmash() {
        for(int work = 0; work < 6 && smashCursor >= 0 && smashCursor < 147; work++) {
            int cursor = smashCursor++, dx = cursor % 7 - 3, dz = cursor / 7 % 7 - 3, dy = cursor / 49 - 1;
            if(dx * dx + dz * dz <= 10 && smashRemoved < 18
                    && WretchCamps.breakNatural(this, smashX + dx, smashY + dy, smashZ + dz)) smashRemoved++;
        }
        if(smashCursor >= 147) smashCursor = -1;
    }
    public AxisAlignedBB partBox(int slot) {
        if(slot == 4) {
            double angle = Math.toRadians(rotationYaw);
            Vec3 a = Vec3.createVectorHelper(posX - Math.sin(angle) * 1.2D, posY + 3D, posZ + Math.cos(angle) * 1.2D);
            Vec3 b = Vec3.createVectorHelper(posX - Math.sin(angle) * 2.6D, posY + 3.05D, posZ + Math.cos(angle) * 2.6D);
            return GardenerPose.bounds(a, b, 0.5D);
        }
        int arm = MathHelper.clamp_int(slot / 2, 0, 1), segment = slot % 2;
        return GardenerPose.bounds(WretchPose.point(this, arm, segment, 1), WretchPose.point(this, arm, segment + 1, 1), WretchPose.ARM_RADIUS);
    }
    public boolean hitPart(int slot, DamageSource source, float damage) {
        if(worldObj.isRemote || !isAwake() || slot < 0 || slot >= 5) return false;
        if(slot < 4) {
            Vec3 a = WretchPose.point(this, slot / 2, slot % 2, 1), b = WretchPose.point(this, slot / 2, slot % 2 + 1, 1);
            if(source.isProjectile() && source.getSourceOfDamage() != null) {
                Entity shot = source.getSourceOfDamage(); Vec3 from = Vec3.createVectorHelper(shot.posX, shot.posY, shot.posZ);
                if(!GardenerPose.rayTouches(a, b, from, from.addVector(shot.motionX, shot.motionY, shot.motionZ), WretchPose.ARM_RADIUS + shot.width * 0.5D)) return false;
            } else if(source.getEntity() instanceof EntityPlayer && !source.isExplosion()) {
                EntityPlayer p = (EntityPlayer) source.getEntity(); Vec3 eye = p.getPosition(1).addVector(0, p.getEyeHeight(), 0), look = p.getLookVec();
                double reach = p.capabilities.isCreativeMode ? 6D : 4.5D;
                Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
                if(!GardenerNests.loaded(worldObj, GardenerPose.bounds(eye, end, 1D))) return false;
                MovingObjectPosition wall = worldObj.func_147447_a(eye, end, false, true, false);
                if(!GardenerPose.rayTouches(a, b, eye, wall == null ? end : wall.hitVec, WretchPose.ARM_RADIUS)) return false;
            }
        }
        boolean hit = attackEntityFrom(source, damage);
        if(hit && slot < 4 && damage >= 5F && (state() == GRAB || state() == HOLD || state() == THROW)) recovery();
        return hit;
    }
    @Override public boolean attackEntityFrom(DamageSource source, float damage) {
        if(worldObj.isRemote || !active() || damage <= 0 || Float.isNaN(damage) || Float.isInfinite(damage)
                || source.getEntity() instanceof IGeminiCreature) return false;
        float before = getHealth();
        boolean result = super.attackEntityFrom(source, damage);
        if(result && isEntityAlive()) {
            interruptDamage += Math.max(0, before - getHealth());
            if(interruptDamage >= 12F && (state() == GRAB || state() == HOLD || state() == THROW || state() == FEED)) recovery();
        }
        return result;
    }
    private void maintainParts() {
        for(int i = 0; i < parts.length; i++) if(parts[i] == null || parts[i].isDead || !parts[i].addedToChunk) {
            if(parts[i] != null) parts[i].setDead();
            EntityWretchPart part = new EntityWretchPart(worldObj); part.attach(this, i);
            if(GardenerNests.loaded(worldObj, part.boundingBox.expand(1, 0, 1)) && worldObj.spawnEntityInWorld(part)) parts[i] = part;
        }
    }
    private void removeParts() { for(int i = 0; i < parts.length; i++) if(parts[i] != null) { parts[i].setDead(); parts[i] = null; } }
    @Override public void onDeath(DamageSource source) {
        super.onDeath(source);
        if(!worldObj.isRemote && dead) { release(); removeParts(); WretchCamps.defeated(this); }
    }
    @Override protected void dropFewItems(boolean player, int looting) { dropItem(Items.bone, 8 + rand.nextInt(5)); dropItem(Items.rotten_flesh, 12 + rand.nextInt(9)); }
    @Override public void setDead() { if(!worldObj.isRemote) { release(); removeParts(); } super.setDead(); }
    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("WretchCamp", campBound); nbt.setInteger("CampX", homeX); nbt.setInteger("CampY", homeY); nbt.setInteger("CampZ", homeZ);
        nbt.setInteger("WretchCooldown", cooldown); nbt.setInteger("FeedCooldown", feedCooldown); nbt.setInteger("LastAttack", lastAttack);
        // In-flight attacks/captures are transient. Vanilla NBT owns health, attributes and position.
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt); fromSave = true;
        campBound = nbt.getBoolean("WretchCamp"); homeX = nbt.getInteger("CampX"); homeY = nbt.getInteger("CampY"); homeZ = nbt.getInteger("CampZ");
        cooldown = Math.max(80, MathHelper.clamp_int(nbt.getInteger("WretchCooldown"), 0, 200));
        feedCooldown = Math.max(200, MathHelper.clamp_int(nbt.getInteger("FeedCooldown"), 0, 600));
        lastAttack = MathHelper.clamp_int(nbt.getInteger("LastAttack"), SWIPE, GRAB);
        captive = null; meal = null; begin(IDLE);
    }
}
