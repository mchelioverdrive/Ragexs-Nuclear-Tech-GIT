package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import com.hbm.world.gemini.MockerySpawning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IEntityLivingData;
import com.hbm.world.gemini.GeminiSpawning;
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
public class EntityGardener extends EntityLiving implements IMob, IBossDisplayData, IGeminiCreature {
    public static final int IDLE = 0, REACH = 1, HOLD = 2, SLAM = 3, RECOVER = 4;
    private final EntityGardenerTentacle[] parts = new EntityGardenerTentacle[GardenerPose.ARMS * GardenerPose.SEGMENTS];
    private final float[] limbHealth = {32, 32, 32, 32, 32, 32};
    private final int[] recovery = new int[6], limbHurt = new int[6];
    private final Set<UUID> struck = new HashSet<UUID>();
    private Entity captive, preyTarget;
    protected double nestX, nestY, nestZ;
    private double travelX, travelY, travelZ;
    private float previousCrawl, crawl;
    private boolean anchored, fromSave;
    private int cooldown = 60, attackTicks, summons, summonTicks;
    private double captureSideX, captureSideZ, captureBestDistance;
    private int captureProgressTick, lastFeedingTick, captureSneakTicks;
    private int smashCursor = -1, smashX, smashY, smashZ, smashRemoved;

    public EntityGardener(World world) {
        super(world); setSize(5.8F, 8F); stepHeight = 1F; experienceValue = 80; func_110163_bv();
        ignoreFrustumCheck = true; setHealth(getMaxHealth());
    }
    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        GeminiSpawning.initialize(this);
        return super.onSpawnWithEgg(data);
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
        dataWatcher.addObject(29, 0F); // Locomotion intensity, interpolated by the client
        dataWatcher.addObject(30, 0xFFFFFF); // Six four-bit underground limb extensions
    }
    @Override protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(360D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.08D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(24D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }
    @Override protected void updateEntityActionState() { }
    @Override protected boolean canDespawn() { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public void applyEntityCollision(Entity other) { }
    @Override public void moveEntity(double x, double y, double z) { }
    @Override protected void fall(float distance) { }
    @Override public boolean isInRangeToRenderDist(double distance) { return distance < 192D * 192D; }
    @Override public float getEyeHeight() { return 6.8F; }
    public boolean isOcean() { return false; }
    public double bodyOffset() { return 0D; }
    private double preyDistanceSq(Entity target) {
        double dx = target.posX - posX, dy = target.posY - posY - bodyOffset(), dz = target.posZ - posZ;
        return dx * dx + dy * dy + dz * dz;
    }
    protected double attackReach() { return 17D; }
    protected double territoryRadius() { return 22D; }
    protected boolean environmentActive() {
        return GeminiSpawning.manualAdmission(this) || nestY >= 8 && nestY <= 44 && GardenerNests.active(worldObj)
                && worldObj.blockExists(MathHelper.floor_double(nestX), MathHelper.floor_double(nestY + 9), MathHelper.floor_double(nestZ))
                && !worldObj.canBlockSeeTheSky(MathHelper.floor_double(nestX), MathHelper.floor_double(nestY + 9), MathHelper.floor_double(nestZ));
    }
    public float crawlAmount(float partial) { return previousCrawl + (crawl - previousCrawl) * partial; }
    public double bodyShift(float partial) {
        return (isAwake() ? Math.sin((worldObj.getTotalWorldTime() + partial) * 0.026D) * 0.12D : 0)
                - crawlAmount(partial) * (isOcean() ? 0.12D : 0.55D);
    }
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
    public double limbExtension(int arm) { return (dataWatcher.getWatchableObjectInt(30) >> (arm * 4) & 15) / 15D; }
    public Vec3 limbAim(float partial) {
        Entity target = eyeTarget();
        if(target != null && (state() == HOLD || state() == REACH)) {
            double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial
                    - lastTickPosX - (posX - lastTickPosX) * partial;
            double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial
                    + target.boundingBox.minY - target.posY + target.height * 0.5D
                    - lastTickPosY - (posY - lastTickPosY) * partial;
            double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial
                    - lastTickPosZ - (posZ - lastTickPosZ) * partial;
            // During the ocean lift the real victim owns the contact point, including escape slack.
            if(state() == HOLD) return Vec3.createVectorHelper(x, y, z);
            double length = Math.sqrt(x * x + z * z), scale = length > attackReach() ? attackReach() / length : 1D;
            return Vec3.createVectorHelper(x * scale, MathHelper.clamp_double(y,
                    bodyOffset() + (isOcean() ? -28 : 0.3), bodyOffset() + (isOcean() ? 31 : 8)), z * scale);
        }
        return Vec3.createVectorHelper(aimX(), aimY(), aimZ());
    }
    public boolean armDisabled(int arm) { return arm < 0 || arm >= 6 || (dataWatcher.getWatchableObjectByte(27) & (1 << arm)) != 0; }
    public Entity eyeTarget() {
        Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
        return entity != null && entity.isEntityAlive() ? entity : null;
    }
    public void anchor(double x, double y, double z) {
        nestX = x; nestY = y; nestZ = z; anchored = true;
        setPosition(x, y, z);
    }
    private boolean active() {
        return isEntityAlive() && anchored && GardenerNests.loaded(worldObj,
                GeminiSpawning.isManual(this) ? boundingBox : visualBounds()) && environmentActive();
    }
    public AxisAlignedBB visualBounds() {
        return boundingBox.expand(isOcean() ? 32 : 18, isOcean() ? 32 : 2, isOcean() ? 32 : 18);
    }
    public boolean hasSpawnSpace() {
        boolean manual = GeminiSpawning.isManual(this);
        if(!GardenerNests.loaded(worldObj, manual ? boundingBox : visualBounds()) || !worldObj.checkNoEntityCollision(boundingBox)
                || !worldObj.getCollidingBoundingBoxes(this, boundingBox).isEmpty() || worldObj.isAnyLiquid(boundingBox)) return false;
        // Eggs need a safe body placement, not a fully excavated natural nest.
        if(manual) return true;
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) if(!clearLimb(arm)) return false;
        return true;
    }
    protected boolean inTerritory(Entity target) {
        double dx = target.posX - nestX, dz = target.posZ - nestZ;
        return dx * dx + dz * dz <= territoryRadius() * territoryRadius()
                && Math.abs(target.posY - nestY) < (isOcean() ? 32D : 8D);
    }
    protected boolean suitablePrey(Entity target) {
        return target instanceof EntityLivingBase && target.width <= 2.2F && target.height <= 3F
                && target.ridingEntity == null && target.riddenByEntity == null
                || isVehicle(target);
    }
    protected boolean isVehicle(Entity target) {
        return target instanceof net.minecraft.entity.item.EntityBoat || target instanceof com.hbm.entity.item.EntityBoatRubber
                || !(target instanceof EntityLivingBase) && target.riddenByEntity != null
                    && target.canBeCollidedWith() && target.width <= 8F && target.height <= 8F;
    }
    private boolean prey(Entity target) {
        if(target == null || target == this || target instanceof IGeminiCreature || target instanceof IBossDisplayData
                || !target.isEntityAlive() || target.worldObj != worldObj || !target.addedToChunk
                || !suitablePrey(target)
                || target.getEntityData().hasKey("MockeryCaptor")
                || target.getEntityData().getLong("GardenerEscapeUntil") > worldObj.getTotalWorldTime()) return false;
        if(target instanceof EntityPlayer && (((EntityPlayer) target).capabilities.disableDamage
                || ((EntityPlayer) target).capabilities.isCreativeMode)) return false;
        if(target.riddenByEntity instanceof EntityPlayer && (((EntityPlayer) target.riddenByEntity).capabilities.disableDamage
                || ((EntityPlayer) target.riddenByEntity).capabilities.isCreativeMode)) return false;
        return inTerritory(target) && preyDistanceSq(target) <= (attackReach() + 7D) * (attackReach() + 7D);
    }
    private boolean reach(Entity target, double range) {
        return prey(target) && preyDistanceSq(target) <= range * range
                && GardenerNests.loaded(worldObj, boundingBox.addCoord(target.posX - posX, target.posY - posY, target.posZ - posZ).expand(1, 1, 1))
                && worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + getEyeHeight(), posZ),
                    Vec3.createVectorHelper(target.posX, target.boundingBox.minY + target.height * 0.5D, target.posZ), false, true, false) == null;
    }
    private void findTarget() {
        Entity closest = null;
        double best = Double.MAX_VALUE;
        int examined = 0;
        double scan = attackReach() + 7D;
        for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(scan, isOcean() ? 32 : 8, scan))) {
            Entity target = (Entity) entry;
            if(!prey(target)) continue;
            if(++examined > 32) break;
            if(!reach(target, attackReach() + (target instanceof EntityPlayer ? 7D : 0D))) continue;
            if(!isOcean() && target instanceof EntityLivingBase) {
                int x = MathHelper.floor_double(target.posX), y = MathHelper.floor_double(target.boundingBox.minY), z = MathHelper.floor_double(target.posZ);
                if(worldObj.getBlock(x, y - 1, z) == com.hbm.blocks.ModBlocks.gardener_flesh
                        || worldObj.getBlock(x, y, z) == com.hbm.blocks.ModBlocks.mockery_growth) {
                    EntityLivingBase living = (EntityLivingBase) target;
                    net.minecraft.potion.PotionEffect effect = living.getActivePotionEffect(net.minecraft.potion.Potion.moveSlowdown);
                    if(effect == null || effect.getAmplifier() <= 1)
                        living.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.moveSlowdown.id, 30, 1));
                }
            }
            double score = preyDistanceSq(target) + (target instanceof EntityPlayer ? 0 : 400D);
            if(score < best) { best = score; closest = target; }
        }
        preyTarget = closest;
        setAttackTarget(closest instanceof EntityLivingBase ? (EntityLivingBase) closest : null);
        dataWatcher.updateObject(20, closest == null ? -1 : closest.getEntityId());
        if(closest instanceof EntityLivingBase) com.hbm.world.gemini.GeminiPopulation.alert(this, (EntityLivingBase) closest);
    }
    private void aim(Entity target, boolean ground) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        double scale = length > attackReach() ? attackReach() / length : 1D;
        dataWatcher.updateObject(24, (float) (dx * scale));
        dataWatcher.updateObject(26, (float) (dz * scale));
        double y = target.boundingBox.minY + target.height * 0.5D - posY;
        if(ground) {
            int x = MathHelper.floor_double(posX + dx * scale), z = MathHelper.floor_double(posZ + dz * scale);
            y = 0.3D;
            for(int dy = 3; dy >= -2; dy--) if(worldObj.getBlock(x, MathHelper.floor_double(posY) + dy - 1, z).getMaterial().blocksMovement()) { y = dy + 0.25D; break; }
        }
        dataWatcher.updateObject(25, (float) MathHelper.clamp_double(y,
                bodyOffset() + (isOcean() ? -28D : 0.3D), bodyOffset() + (isOcean() ? 31D : 8D)));
    }
    private void begin(int next) {
        dataWatcher.updateObject(21, (byte) next);
        dataWatcher.updateObject(22, (int) worldObj.getTotalWorldTime());
        attackTicks = 0; struck.clear();
    }
    private void release() {
        release(true);
    }
    private void release(boolean escaped) {
        if(captive != null) {
            if(escaped) captive.getEntityData().setLong("GardenerEscapeUntil", worldObj.getTotalWorldTime() + 400L);
            captive.velocityChanged = true;
        }
        captive = null;
        preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1);
        if(state() != IDLE && state() != RECOVER) { begin(RECOVER); cooldown = 45; }
    }

    @Override public void onUpdate() {
        motionX = motionY = motionZ = 0;
        super.onUpdate();
        previousCrawl = crawl;
        crawl += (dataWatcher.getWatchableObjectFloat(29) - crawl) * 0.25F;
        if(worldObj.isRemote) return;
        if(!anchored) anchor(posX, posY, posZ);
        if(!active()) {
            release(); preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); dataWatcher.updateObject(28, (byte) 0);
            if(state() != IDLE) begin(IDLE);
            dataWatcher.updateObject(29, 0F);
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
        Entity target = preyTarget;
        if(!prey(target)) { preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); target = null; }
        tickLocomotion(target);
        if(ticksExisted % 10 == 1 || dataWatcher.getWatchableObjectFloat(29) > 0) adaptLimbs();
        if(cooldown > 0) cooldown--;
        if(state() == IDLE && target != null && cooldown == 0 && reach(target, attackReach())) {
            int arm = -1;
            double best = Double.MAX_VALUE;
            for(int i = 0; i < 6; i++) if(!armDisabled(i)) {
                Vec3 shoulder = GardenerPose.point(this, i, 1, 1);
                double score = shoulder.squareDistanceTo(Vec3.createVectorHelper(target.posX, target.posY, target.posZ));
                if(score < best) { best = score; arm = i; }
            }
            if(arm >= 0) {
                dataWatcher.updateObject(23, (byte) arm);
                boolean slam = !isOcean() && rand.nextInt(getHealth() < getMaxHealth() * 0.4F ? 2 : 3) == 0;
                aim(target, slam); begin(slam ? SLAM : REACH);
                playSound("mob.ghast.warn", 0.65F, 0.5F);
            }
        }
        if(state() != IDLE) tickAttack();
        if(!isOcean() && target != null && ++summonTicks >= (getHealth() < getMaxHealth() * 0.5F ? 200 : 400)) {
            summonTicks = 0; summon();
        }
        for(EntityGardenerTentacle part : parts) if(part != null && !part.isDead) part.follow();
        tickSmash();
    }
    private void tickAttack() {
        attackTicks++;
        if(state() == RECOVER) { if(attackTicks >= 24) begin(IDLE); return; }
        if(armDisabled(attackArm())) { release(); return; }
        Entity target = preyTarget;
        if(state() == HOLD) {
            tickCapture();
            return;
        }
        if(state() == REACH && attackTicks <= 34 && reach(target, attackReach())) aim(target, false);
        if(state() == REACH && attackTicks >= 24 && attackTicks <= 34) {
            Vec3 a = GardenerPose.point(this, attackArm(), 3, 1), b = GardenerPose.point(this, attackArm(), 4, 1);
            for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, GardenerPose.bounds(a, b, GardenerPose.RADIUS))) {
                Entity victim = (Entity) entry;
                if(!reach(victim, attackReach()) || !clearLimb(attackArm()) || !GardenerPose.touches(a, b, victim.boundingBox, GardenerPose.RADIUS)) continue;
                captive = preyTarget = victim; setAttackTarget(victim instanceof EntityLivingBase ? (EntityLivingBase) victim : null); dataWatcher.updateObject(20, victim.getEntityId());
                begin(HOLD);
                {
                    double dx = victim.posX - posX, dz = victim.posZ - posZ;
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    captureSideX = distance > 0.01D ? dx / distance : 1D;
                    captureSideZ = distance > 0.01D ? dz / distance : 0D;
                    captureBestDistance = captureDistance(); captureProgressTick = lastFeedingTick = captureSneakTicks = 0;
                }
                if(victim instanceof EntityLivingBase) GeminiInfections.expose((EntityLivingBase) victim, "gardener_capture:" + getUniqueID(), "gardener");
                else wound(victim, 10F);
                return;
            }
        }
        if(state() == SLAM && attackTicks >= 23 && attackTicks <= 28) {
            for(int segment = 0; segment < GardenerPose.SEGMENTS; segment++) {
                Vec3 a = GardenerPose.point(this, attackArm(), segment, 1), b = GardenerPose.point(this, attackArm(), segment + 1, 1);
                for(Object entry : worldObj.getEntitiesWithinAABB(EntityLivingBase.class, GardenerPose.bounds(a, b, GardenerPose.RADIUS))) {
                    EntityLivingBase victim = (EntityLivingBase) entry;
                    if(prey(victim) && !struck.contains(victim.getUniqueID()) && reach(victim, attackReach()) && clearLimb(attackArm())
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
        if(attackTicks >= 44) { begin(RECOVER); cooldown = getHealth() < getMaxHealth() * 0.4F ? 20 : 55; }
    }
    private boolean clearLimb(int arm) {
        return clearLimb(arm, limbExtension(arm));
    }
    private boolean clearLimb(int arm, double extension) {
        for(int i = 0; i < GardenerPose.SEGMENTS; i++) {
            Vec3 a = GardenerPose.point(this, arm, i, 1, extension), b = GardenerPose.point(this, arm, i + 1, 1, extension);
            if(!GardenerNests.loaded(worldObj, GardenerPose.bounds(a, b, 0.4D))
                    || worldObj.func_147447_a(a, b, false, true, false) != null) return false;
        }
        return true;
    }
    private void adaptLimbs() {
        if(isOcean() || !GeminiSpawning.isManual(this)) return;
        int extensions = dataWatcher.getWatchableObjectInt(30);
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) {
            if((arm == attackArm() && state() != IDLE) || armDisabled(arm)) continue;
            int extension = 15;
            while(extension > 0 && !clearLimb(arm, extension / 15D)) extension--;
            // Leave room for idle sway between probes instead of resting the tip against a wall.
            if(extension < 15 && extension > 0) extension--;
            extensions = (extensions & ~(15 << (arm * 4))) | (extension << (arm * 4));
        }
        dataWatcher.updateObject(30, extensions);
    }
    private Vec3 captureGoal() {
        // Finish beside the upper body, outside its collision column even for a wide vehicle.
        double radius = Math.max(2.8D, width * 0.5D + captive.width * 0.5D + 0.75D);
        return Vec3.createVectorHelper(posX + captureSideX * radius, posY + bodyOffset() + (isOcean() ? 2D : 2.5D),
                posZ + captureSideZ * radius);
    }
    private double captureDistance() {
        return captureGoal().distanceTo(Vec3.createVectorHelper(captive.posX,
                captive.boundingBox.minY + captive.height * 0.5D, captive.posZ));
    }
    private void tickCapture() {
        // Acquisition's eye ray and strict reach are not the attachment contract. The actual
        // shoulder-to-victim skeleton is checked instead, with a little slack for native movement.
        if(captive != null && !captive.isEntityAlive()) { release(false); return; }
        if(captive == null || !prey(captive) || worldObj.getEntityByID(captive.getEntityId()) != captive
                || preyDistanceSq(captive) > (attackReach() + 2D) * (attackReach() + 2D)
                || !GardenerNests.loaded(worldObj, captive.boundingBox.expand(1, 0, 1))
                || !clearLimb(attackArm())) { release(); return; }
        aim(captive, false);
        captureSneakTicks = captive instanceof EntityPlayer && captive.isSneaking() ? captureSneakTicks + 1 : 0;
        if(captureSneakTicks >= 20) { release(); return; }
        double distance = captureDistance();
        if(distance <= 1.25D && attackTicks - lastFeedingTick >= 20) {
            lastFeedingTick = attackTicks;
            if(wound(captive, isOcean() ? 8F : 4F)) {
                captureProgressTick = attackTicks;
                playSound("mob.slime.attack", 0.8F, isOcean() ? 0.6F : 0.75F);
            }
            if(!captive.isEntityAlive()) { release(false); return; }
        }
        if(distance < captureBestDistance - 0.25D) {
            captureBestDistance = distance; captureProgressTick = attackTicks;
        }
        // Measure achieved movement, not requested velocity: roofs, AI and escape attempts can stall it.
        if(attackTicks - captureProgressTick >= 120 || attackTicks >= 1200) { release(); return; }
        Vec3 goal = captureGoal();
        double dx = goal.xCoord - captive.posX, dz = goal.zCoord - captive.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double scale = horizontal > 0 ? Math.min(0.24D / horizontal, 0.12D) : 0;
        double vy = MathHelper.clamp_double((goal.yCoord - captive.boundingBox.minY - captive.height * 0.5D) * 0.12D,
                -0.2D, 0.36D);
        if(!GardenerNests.loaded(worldObj, captive.boundingBox.addCoord(dx * scale, vy, dz * scale).expand(1, 0, 1))) {
            release(); return;
        }
        // Apply a short native collision-resolved movement on the owning server thread. A mere
        // requested velocity can be overwritten by victim AI or rejected by player movement packets.
        captive.moveEntity(dx * scale, vy, dz * scale);
        captive.motionX = captive.motionY = captive.motionZ = 0; captive.fallDistance = 0;
        if(captive instanceof net.minecraft.entity.player.EntityPlayerMP) {
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) captive;
            HeldPlayer.support(player);
            if(attackTicks % 2 == 0 && player.playerNetServerHandler != null)
                player.playerNetServerHandler.setPlayerLocation(captive.posX, captive.posY, captive.posZ, captive.rotationYaw, captive.rotationPitch);
        }
        captive.velocityChanged = true;
    }
    private static final class HeldPlayer {
        private static final java.lang.reflect.Field FLOATING = cpw.mods.fml.relauncher.ReflectionHelper.findField(
                net.minecraft.network.NetHandlerPlayServer.class, "floatingTickCount", "field_147365_f");
        static void support(net.minecraft.entity.player.EntityPlayerMP player) {
            if(player.playerNetServerHandler == null) return;
            try { FLOATING.setInt(player.playerNetServerHandler, 0); }
            catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot validate Gardener tentacle support", ex); }
        }
    }
    public boolean isHolding(Entity entity) { return captive == entity && state() == HOLD && isEntityAlive(); }
    public void releaseForChunkUnload(int x, int z) {
        if(chunkCoordX == x && chunkCoordZ == z || captive != null && captive.chunkCoordX == x && captive.chunkCoordZ == z) release();
    }
    /** Short collision-tested steering probes instead of an enormous vanilla cave path search. */
    private void tickLocomotion(Entity target) {
        if(state() != IDLE) { dataWatcher.updateObject(29, 0F); return; }
        if(ticksExisted % 10 == 1) {
            travelX = travelY = travelZ = 0;
            double gx = nestX, gy = nestY, gz = nestZ;
            if(target != null) {
                gx = target.posX; gz = target.posZ;
                // Ocean prey is reached by the hanging limbs; retain the safe seabed-relative body altitude.
            } else if(ticksExisted % 160 < 80) {
                double angle = (ticksExisted / 160 + getEntityId()) * 2.39996D;
                gx += Math.cos(angle) * (isOcean() ? 6 : 3);
                gz += Math.sin(angle) * (isOcean() ? 6 : 3);
            }
            double dx = gx - posX, dz = gz - posZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double stop = target == null ? 0.7D : isOcean() ? 4D : 8D;
            double speed = getEntityAttribute(SharedMonsterAttributes.movementSpeed).getAttributeValue();
            if(distance > stop || isOcean() && Math.abs(gy - posY) > 0.5D) {
                double angle = Math.atan2(dz, dx);
                for(int attempt = 0; attempt < 5; attempt++) {
                    double turn = attempt == 0 ? 0 : (attempt % 2 == 0 ? -1 : 1) * ((attempt + 1) / 2) * 0.65D;
                    double vx = distance > stop ? Math.cos(angle + turn) * speed : 0;
                    double vz = distance > stop ? Math.sin(angle + turn) * speed : 0;
                    double vy = travelVertical(gy, speed);
                    double nx = posX + vx * 12, nz = posZ + vz * 12;
                    if((nx - nestX) * (nx - nestX) + (nz - nestZ) * (nz - nestZ) > territoryRadius() * territoryRadius()) continue;
                    if(canTravel(vx * 12, vy * 12, vz * 12)) { travelX = vx; travelY = vy; travelZ = vz; break; }
                }
            }
        }
        double ox = posX, oy = posY, oz = posZ;
        if(canTravel(travelX, travelY, travelZ)) moveGardener(travelX, isOcean() ? travelY : -0.15D, travelZ);
        double moved = Math.abs(posX - ox) + Math.abs(posY - oy) + Math.abs(posZ - oz);
        dataWatcher.updateObject(29, moved > 0.005D ? 1F : 0F);
    }
    protected double travelVertical(double goalY, double speed) {
        return isOcean() ? MathHelper.clamp_double(goalY - posY, -speed * 0.5D, speed * 0.5D) : 0;
    }
    /** Bypass the public physics lock only for the authoritative territorial steering. */
    protected void moveGardener(double x, double y, double z) { super.moveEntity(x, y, z); }
    protected boolean canTravel(double x, double y, double z) {
        AxisAlignedBB next = boundingBox.getOffsetBoundingBox(x, y, z);
        if(!GardenerNests.loaded(worldObj, next.expand(GeminiSpawning.isManual(this) ? 1 : 19, 2,
                GeminiSpawning.isManual(this) ? 1 : 19)) || Math.abs(next.minY - nestY) > 4D
                || worldObj.isAnyLiquid(next)) return false;
        if(!worldObj.getCollidingBoundingBoxes(this, next).isEmpty()) {
            // Native stepHeight handles one-block ledges after this clearance probe.
            next = next.getOffsetBoundingBox(0, 1, 0);
            if(!worldObj.getCollidingBoundingBoxes(this, next).isEmpty()) return false;
        }
        // Four support corners prevent a wide body from walking out over ordinary cave drops.
        for(int corner = 0; corner < 4; corner++) {
            int bx = MathHelper.floor_double((corner % 2 == 0 ? next.minX : next.maxX) + (corner % 2 == 0 ? 0.5D : -0.5D));
            int bz = MathHelper.floor_double((corner < 2 ? next.minZ : next.maxZ) + (corner < 2 ? 0.5D : -0.5D));
            int by = MathHelper.floor_double(next.minY - 0.1D);
            if(!worldObj.getBlock(bx, by, bz).getMaterial().blocksMovement()
                    && !worldObj.getBlock(bx, by - 1, bz).getMaterial().blocksMovement()) return false;
        }
        return true;
    }
    private boolean wound(Entity victim, float damage) {
        // Limb contacts are already validated above. Use explicit exposure to avoid inventing body melee contact.
        float health = victim instanceof EntityLivingBase ? ((EntityLivingBase) victim).getHealth() : 0;
        boolean hit = victim.attackEntityFrom(DamageSource.causeMobDamage(this), damage)
                && (!(victim instanceof EntityLivingBase) || ((EntityLivingBase) victim).getHealth() < health);
        if(hit && victim instanceof EntityLivingBase && victim.isEntityAlive())
            GeminiInfections.expose((EntityLivingBase) victim, "gardener_wound:" + getUniqueID(), "gardener");
        return hit;
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
            Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ), look = player.getLookVec();
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
        nbt.setInteger("GardenerFormVersion", 2);
        for(int i = 0; i < 6; i++) { nbt.setFloat("LimbHealth" + i, limbHealth[i]); nbt.setInteger("LimbRecovery" + i, recovery[i]); }
        // Captures and in-flight attacks deliberately end on reload. No restraint is stored on the victim.
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt); fromSave = true;
        if(nbt.getInteger("GardenerFormVersion") < 2 && !isOcean()
                && getEntityAttribute(SharedMonsterAttributes.movementSpeed).getBaseValue() == 0D)
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.08D);
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
