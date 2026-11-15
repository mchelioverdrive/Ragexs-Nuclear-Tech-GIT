package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.main.MainRegistry;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import com.hbm.world.gemini.MockerySpawning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.IEntityLivingData;
import com.hbm.world.gemini.GeminiSpawning;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A nest-bound miniboss. Captives stay real entities with server-owned transport. */
public class EntityGardener extends EntityLiving implements IMob, IBossDisplayData, IGeminiCreature {
    public static final int IDLE = 0, REACH = 1, HOLD = 2, SLAM = 3, RECOVER = 4;
    public static final int CAPTURE = 5, LIFT = 6, ATTACH = 7, WRAP = 8, RELEASE = 9;
    private final EntityGardenerTentacle[] parts = new EntityGardenerTentacle[GardenerPose.ARMS * GardenerPose.SEGMENTS];
    private final float[] limbHealth = {32, 32, 32, 32, 32, 32};
    private final int[] recovery = new int[6], limbHurt = new int[6];
    private final Set<UUID> struck = new HashSet<UUID>();
    private Entity captive, preyTarget;
    private EntityGardenerCocoon pendingCocoon;
    private ChunkCoordinates selectedAttachment = new ChunkCoordinates(0, -1, 0);
    private final List<SurfaceBiomass> surfaceBiomass = new ArrayList<SurfaceBiomass>();
    private boolean placingSurface;
    private boolean feeding;
    private String cocoonAdmissionFailure;
    private net.minecraftforge.event.entity.living.LivingDeathEvent feedingDeath;
    protected double nestX, nestY, nestZ;
    private double travelX, travelY, travelZ;
    private float previousCrawl, crawl;
    private boolean anchored, fromSave;
    private int cooldown = 60, attackTicks, summons, summonTicks;
    private double captureSideX, captureSideZ, captureBestDistance;
    private int captureProgressTick, lastFeedingTick, captureSneakTicks, captureTicks;
    private int movementObstructionTicks;
    private List<Vec3> captiveRoute;
    private Vec3 routeGoal, safeCaptivePosition;
    private boolean traversingBlocks;
    private HeldNavigation heldNavigation;
    private int smashCursor = -1, smashX, smashY, smashZ, smashRemoved;
    private static final boolean CAPTURE_DIAGNOSTICS = Boolean.getBoolean("hbm.gardenerCaptureDiagnostics");
    private long nextCaptureDiagnostic;

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
        dataWatcher.addObject(31, new ChunkCoordinates(0, -1, 0)); // Selected hanging attachment, or feeding.
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
        if(!isOcean()) {
            // Distance to the occupied body volume has no blind spot beneath its center.
            AxisAlignedBB box = target.boundingBox;
            double dx = Math.max(boundingBox.minX - box.maxX, Math.max(0D, box.minX - boundingBox.maxX));
            double dy = Math.max(boundingBox.minY - box.maxY, Math.max(0D, box.minY - boundingBox.maxY));
            double dz = Math.max(boundingBox.minZ - box.maxZ, Math.max(0D, box.minZ - boundingBox.maxZ));
            return dx * dx + dy * dy + dz * dz;
        }
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
    public boolean captureState() { return state() == HOLD || state() >= CAPTURE && state() <= WRAP; }
    public boolean hangingCapture() { return !isOcean() && captureState() && selectedAttachment.posY >= 0; }
    public ChunkCoordinates cocoonAttachment() { return new ChunkCoordinates(selectedAttachment); }
    @Override public void func_145781_i(int id) {
        super.func_145781_i(id);
        // 1.7.10 supports coordinate metadata but has no typed coordinate getter.
        if(id == 31) for(Object entry : dataWatcher.getAllWatched()) {
            DataWatcher.WatchableObject value = (DataWatcher.WatchableObject) entry;
            if(value.getDataValueId() == 31) { selectedAttachment = new ChunkCoordinates((ChunkCoordinates) value.getObject()); break; }
        }
    }
    public Vec3 limbAim(float partial) {
        Entity target = eyeTarget();
        if(target != null && (captureState() || state() == REACH)) {
            double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial
                    - lastTickPosX - (posX - lastTickPosX) * partial;
            double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial
                    + target.boundingBox.minY - target.posY + target.height * 0.5D
                    - lastTickPosY - (posY - lastTickPosY) * partial;
            double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial
                    - lastTickPosZ - (posZ - lastTickPosZ) * partial;
            // During the ocean lift the real victim owns the contact point, including escape slack.
            if(captureState()) return Vec3.createVectorHelper(x,
                    y + (isOcean() ? 0 : gripHeight(target) - target.height * 0.5D), z);
            if(!isOcean() && state() == REACH) y += gripHeight(target) - target.height * 0.5D;
            double length = Math.sqrt(x * x + z * z), scale = length > attackReach() ? attackReach() / length : 1D;
            return Vec3.createVectorHelper(x * scale, MathHelper.clamp_double(y,
                    bodyOffset() + (isOcean() ? -28 : -8), bodyOffset() + (isOcean() ? 31 : 8)), z * scale);
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
        return preyRejection(target) == null;
    }
    private String preyRejection(Entity target) {
        if(target == null) return "No target";
        if(target == this || target instanceof IGeminiCreature || target instanceof IBossDisplayData) return "Excluded creature";
        if(!target.isEntityAlive() || target.worldObj != worldObj || !target.addedToChunk) return "Dead or unloaded prey";
        if(!suitablePrey(target)) return "Unsuitable size or mount";
        if(target.getEntityData().hasKey("MockeryCaptor")) return "Held by Mockery";
        if(target.getEntityData().getLong("WoodcrawlerCocoonUntil") > worldObj.getTotalWorldTime()) return "Woodcrawler cocoon cooldown";
        if(heldByOtherGardener(target)) return "Held by another Gardener";
        if(target.getEntityData().getLong("GardenerEscapeUntil") > worldObj.getTotalWorldTime()) return "Gardener escape cooldown";
        if(target instanceof EntityPlayer && (((EntityPlayer) target).capabilities.disableDamage
                || ((EntityPlayer) target).capabilities.isCreativeMode)) return "Invulnerable player";
        if(target.riddenByEntity instanceof EntityPlayer && (((EntityPlayer) target.riddenByEntity).capabilities.disableDamage
                || ((EntityPlayer) target.riddenByEntity).capabilities.isCreativeMode)) return "Invulnerable rider";
        if(!inTerritory(target)) return "Outside territory";
        if(preyDistanceSq(target) > (attackReach() + 7D) * (attackReach() + 7D)) return "Outside search reach";
        return null;
    }
    /** Ceiling transport reaches from the upper body, rather than the ground-level territory band. */
    boolean canTransportTo(Vec3 point) {
        double dx = point.xCoord - posX, dy = point.yCoord - boundingBox.maxY, dz = point.zCoord - posZ;
        double reach = attackReach() + 2D;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }
    private boolean heldByOtherGardener(Entity target) {
        if(!target.getEntityData().hasKey("GardenerCaptorId")) return false;
        Entity owner = worldObj.getEntityByID(target.getEntityData().getInteger("GardenerCaptorId"));
        return owner != this && owner instanceof EntityGardener && ((EntityGardener) owner).isHolding(target)
                && owner.getUniqueID().toString().equals(target.getEntityData().getString("GardenerCaptor"));
    }
    private boolean reach(Entity target, double range) {
        return reachRejection(target, range) == null;
    }
    private String reachRejection(Entity target, double range) {
        String eligibility = preyRejection(target);
        if(eligibility != null) return eligibility;
        if(preyDistanceSq(target) > range * range) return "Outside bounding-box reach";
        // Use the existing search band beneath the body, including crater floors. Retain
        // the narrower vertical approach limit outside that footprint.
        if(!isOcean() && (target.boundingBox.maxY < boundingBox.minY - (beneathBody(target) ? 8D : 2D)
                || target.boundingBox.minY > boundingBox.maxY + 2D)) return "Outside vertical reach";
        if(!GardenerNests.loaded(worldObj, boundingBox.addCoord(target.posX - posX, target.posY - posY, target.posZ - posZ).expand(1, 1, 1)))
            return "Unloaded approach";
        // Block rays deliberately exclude our own body/limb entity collision boxes.
        if(worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + getEyeHeight(), posZ),
                Vec3.createVectorHelper(target.posX, target.boundingBox.minY + target.height * 0.5D, target.posZ), false, true, false) != null)
            return "Blocked line of sight";
        return null;
    }
    private boolean beneathBody(Entity target) {
        return target != null && target.boundingBox.maxX >= boundingBox.minX && target.boundingBox.minX <= boundingBox.maxX
                && target.boundingBox.maxZ >= boundingBox.minZ && target.boundingBox.minZ <= boundingBox.maxZ
                && target.boundingBox.minY <= boundingBox.maxY;
    }
    private void findTarget() {
        Entity closest = null;
        double best = Double.MAX_VALUE;
        int examined = 0;
        double scan = attackReach() + 7D;
        for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(scan, isOcean() ? 32 : 8, scan))) {
            Entity target = (Entity) entry;
            if(!prey(target)) { captureRejection(target, preyRejection(target)); continue; }
            if(isOcean() && ++examined > 32) break;
            String rejection = reachRejection(target, attackReach() + (target instanceof EntityPlayer ? 7D : 0D));
            if(rejection != null) { captureRejection(target, rejection); continue; }
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
            if(!isOcean()) {
                // Do not abandon a meal under the body for a player outside capture range.
                if(!beneathBody(target)) score += 10000D;
                if(!tipInReach(target)) score += 20000D;
            }
            if(score < best) {
                if(!isOcean() && beneathBody(target) && captureArm(target) < 0) {
                    captureRejection(target, "No enabled tentacle with a clear reaching path"); continue;
                }
                best = score; closest = target;
            }
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
        if(!isOcean() && !ground) y += gripHeight(target) - target.height * 0.5D;
        if(ground) {
            int x = MathHelper.floor_double(posX + dx * scale), z = MathHelper.floor_double(posZ + dz * scale);
            y = 0.3D;
            for(int dy = 3; dy >= -2; dy--) if(worldObj.getBlock(x, MathHelper.floor_double(posY) + dy - 1, z).getMaterial().blocksMovement()) { y = dy + 0.25D; break; }
        }
        dataWatcher.updateObject(25, (float) MathHelper.clamp_double(y,
                bodyOffset() + (isOcean() ? -28D : ground ? 0.3D : -8D), bodyOffset() + (isOcean() ? 31D : 8D)));
    }
    private void begin(int next) {
        dataWatcher.updateObject(21, (byte) next);
        dataWatcher.updateObject(22, (int) worldObj.getTotalWorldTime());
        attackTicks = 0; struck.clear();
    }
    private void captureDiagnostic(String reason) {
        if(captive != null || captureState()) MainRegistry.logger.info("[Gardener capture] gardener=" + getUniqueID()
                + " captive=" + (captive == null ? "unknown" : captive.getUniqueID()) + " state=" + state()
                + " ticks=" + captureTicks + ": " + reason);
    }
    private void captureRejection(Entity target, String reason) {
        if(isOcean() || !CAPTURE_DIAGNOSTICS || reason == null || target == null || !suitablePrey(target)
                || preyDistanceSq(target) > attackReach() * attackReach()
                || worldObj.getTotalWorldTime() < nextCaptureDiagnostic) return;
        nextCaptureDiagnostic = worldObj.getTotalWorldTime() + 100L;
        double dx = target.posX - posX, dz = target.posZ - posZ;
        MainRegistry.logger.info("[Gardener capture rejected] gardener=" + getEntityId() + " target=" + target.getEntityId()
                + " horizontal=" + Math.sqrt(dx * dx + dz * dz) + " vertical=" + (target.boundingBox.minY - boundingBox.minY)
                + " boxSeparation=" + Math.sqrt(preyDistanceSq(target)) + " eligible=" + prey(target)
                + " state=" + state() + " cooldown=" + cooldown + " captive=" + (captive == null ? -1 : captive.getEntityId())
                + " escapeUntil=" + target.getEntityData().getLong("GardenerEscapeUntil") + ": " + reason);
    }
    private void release(String reason) {
        captureDiagnostic(reason);
        release(true);
    }
    private void release(boolean escaped) {
        if(!isOcean() && captive != null) restoreCaptivePosition();
        if(!isOcean() && captive != null) {
            dataWatcher.updateObject(24, (float) (captive.posX - posX));
            dataWatcher.updateObject(25, (float) (captive.boundingBox.minY + gripHeight(captive) - posY));
            dataWatcher.updateObject(26, (float) (captive.posZ - posZ));
        }
        if(heldNavigation != null) { heldNavigation.restore(); heldNavigation = null; }
        if(captive != null) {
            if(getUniqueID().toString().equals(captive.getEntityData().getString("GardenerCaptor"))) {
                captive.getEntityData().removeTag("GardenerCaptor"); captive.getEntityData().removeTag("GardenerCaptorId");
                captive.getEntityData().removeTag("GardenerDragSafe");
            }
            if(escaped) captive.getEntityData().setLong("GardenerEscapeUntil", worldObj.getTotalWorldTime() + 400L);
            captive.velocityChanged = true;
        }
        captive = null;
        if(pendingCocoon != null) pendingCocoon.cancelWrapping();
        pendingCocoon = null;
        dataWatcher.updateObject(31, new ChunkCoordinates(0, -1, 0));
        resetTransport(); safeCaptivePosition = null;
        preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1);
        if(state() != IDLE && state() != RECOVER && state() != RELEASE) { begin(isOcean() ? RECOVER : RELEASE); cooldown = 45; }
    }

    @Override public void onUpdate() {
        motionX = motionY = motionZ = 0;
        super.onUpdate();
        previousCrawl = crawl;
        crawl += (dataWatcher.getWatchableObjectFloat(29) - crawl) * 0.25F;
        if(worldObj.isRemote) return;
        tickSurfaceBiomass();
        if(!anchored) anchor(posX, posY, posZ);
        if(!active()) {
            release(GardenerNests.loaded(worldObj, GeminiSpawning.isManual(this) ? boundingBox : visualBounds())
                    ? "Gardener deactivation" : "Chunk unloading");
            preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); dataWatcher.updateObject(28, (byte) 0);
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
        if(ticksExisted % 20 == 1) {
            maintainParts();
            if(captive == null && (isOcean() ? state() != RELEASE : state() == IDLE)) findTarget();
        }
        Entity target = preyTarget;
        if(captive == null && !prey(target)) {
            captureRejection(target, preyRejection(target));
            preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); target = null;
        }
        tickLocomotion(target);
        if(ticksExisted % 10 == 1 || dataWatcher.getWatchableObjectFloat(29) > 0) adaptLimbs();
        if(cooldown > 0) cooldown--;
        if(state() == IDLE && target != null && cooldown == 0 && reach(target, attackReach()) && (isOcean() || tipInReach(target))) {
            int arm = captureArm(target);
            if(arm >= 0) {
                dataWatcher.updateObject(23, (byte) arm);
                boolean slam = !isOcean() && !beneathBody(target) && rand.nextInt(getHealth() < getMaxHealth() * 0.4F ? 2 : 3) == 0;
                aim(target, slam); begin(slam ? SLAM : REACH);
                playSound("mob.ghast.charge", 0.65F, 0.5F);
            } else {
                captureRejection(target, "No enabled tentacle with a clear reaching path");
                if(!isOcean()) cooldown = 20; // Retry transient obstruction without six probes every tick.
            }
        } else if(!isOcean() && state() == IDLE && target != null) {
            String rejection = cooldown > 0 ? "Attack cooldown" : reachRejection(target, attackReach());
            captureRejection(target, rejection == null ? "Outside tentacle tip reach; approaching prey" : rejection);
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
        if(state() == RECOVER || state() == RELEASE) { if(attackTicks >= 24) begin(IDLE); return; }
        if(armDisabled(attackArm())) { release("Disabled tentacle"); return; }
        Entity target = preyTarget;
        if(captureState()) {
            tickCapture();
            return;
        }
        if(state() == REACH && attackTicks <= 34 && reach(target, attackReach())) aim(target, false);
        if(state() == REACH && attackTicks >= 24 && attackTicks <= 34) {
            Vec3 a = GardenerPose.point(this, attackArm(), 3, 1), b = GardenerPose.point(this, attackArm(), 4, 1);
            List<Entity> contacts = worldObj.getEntitiesWithinAABBExcludingEntity(this, GardenerPose.bounds(a, b, GardenerPose.RADIUS));
            if(!isOcean() && target != null) { contacts.remove(target); contacts.add(0, target); }
            boolean clear = clearLimb(attackArm());
            for(Object entry : contacts) {
                Entity victim = (Entity) entry;
                String rejection = reachRejection(victim, attackReach());
                if(rejection == null && !clear) rejection = "Reaching tentacle obstructed";
                if(rejection == null && !GardenerPose.touches(a, b, victim.boundingBox, GardenerPose.RADIUS)) rejection = "Reaching tip missed prey bounds";
                if(rejection != null) { if(attackTicks == 34 && victim == target) captureRejection(victim, rejection); continue; }
                captive = preyTarget = victim; setAttackTarget(victim instanceof EntityLivingBase ? (EntityLivingBase) victim : null); dataWatcher.updateObject(20, victim.getEntityId());
                begin(isOcean() ? HOLD : CAPTURE);
                victim.getEntityData().setString("GardenerCaptor", getUniqueID().toString());
                victim.getEntityData().setInteger("GardenerCaptorId", getEntityId());
                pendingCocoon = EntityGardenerCocoon.findCeiling(this, victim);
                if(pendingCocoon != null) dataWatcher.updateObject(31, new ChunkCoordinates(
                        MathHelper.floor_double(pendingCocoon.posX), MathHelper.floor_double(pendingCocoon.boundingBox.maxY),
                        MathHelper.floor_double(pendingCocoon.posZ)));
                {
                    double dx = victim.posX - posX, dz = victim.posZ - posZ;
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    captureSideX = distance > 0.01D ? dx / distance : 1D;
                    captureSideZ = distance > 0.01D ? dz / distance : 0D;
                    captureBestDistance = captureDistance(); captureTicks = captureProgressTick = lastFeedingTick = captureSneakTicks = 0;
                    resetTransport();
                    if(!isOcean()) safeCaptivePosition = captiveCenter();
                }
                if(!isOcean()) restrainCaptive();
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
    private int captureArm(Entity target) {
        int arm = -1;
        double best = Double.MAX_VALUE;
        for(int i = 0; i < 6; i++) if(!armDisabled(i)) {
            Vec3 shoulder = GardenerPose.point(this, i, 1, 1);
            double score = shoulder.squareDistanceTo(Vec3.createVectorHelper(target.posX, target.posY, target.posZ));
            if(score < best && (isOcean() || clearReachLimb(i, target))) { best = score; arm = i; }
        }
        return arm;
    }
    /** Check the actual final reaching shape before committing to an arm, using blocks only. */
    private boolean clearReachLimb(int arm, Entity target) {
        Vec3 contact = reachContact(target);
        for(int joint = 0; joint < GardenerPose.SEGMENTS; joint++) {
            Vec3 a = GardenerPose.reachPoint(this, arm, joint, contact);
            Vec3 b = GardenerPose.reachPoint(this, arm, joint + 1, contact);
            AxisAlignedBB area = GardenerPose.bounds(a, b, GardenerPose.RADIUS);
            if(!GardenerNests.loaded(worldObj, area)) return false;
            for(Object entry : worldObj.func_147461_a(area))
                if(GardenerPose.touches(a, b, (AxisAlignedBB) entry, GardenerPose.RADIUS)) return false;
        }
        return true;
    }
    private Vec3 reachContact(Entity target) {
        double dx = target.posX - posX, dz = target.posZ - posZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        double scale = length > attackReach() ? attackReach() / length : 1D;
        return Vec3.createVectorHelper(dx * scale,
                MathHelper.clamp_double(target.boundingBox.minY + gripHeight(target) - posY, -8D, 8D), dz * scale);
    }
    private boolean tipInReach(Entity target) {
        Vec3 tip = reachContact(target).addVector(posX, posY, posZ);
        AxisAlignedBB box = target.boundingBox;
        double dx = Math.max(box.minX - tip.xCoord, Math.max(0D, tip.xCoord - box.maxX));
        double dy = Math.max(box.minY - tip.yCoord, Math.max(0D, tip.yCoord - box.maxY));
        double dz = Math.max(box.minZ - tip.zCoord, Math.max(0D, tip.zCoord - box.maxZ));
        return dx * dx + dy * dy + dz * dz <= GardenerPose.RADIUS * GardenerPose.RADIUS;
    }
    /** Capture probes use solid block shapes, never the victim, our parts or non-solid entity AABBs. */
    private static double gripHeight(Entity victim) {
        // Grasp tiny prey above its center so the tentacle's radius does not intersect its floor.
        return Math.max(victim.height * 0.5D, GardenerPose.RADIUS + 0.06D);
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
        if(pendingCocoon != null) return pendingCocoon.victimGoal(captive);
        if(!isOcean() && captive instanceof EntityLivingBase) {
            // The model narrows to its underside at y=0.8. Feed there, with feet above the
            // nest floor, rather than dragging underbody prey out around the collision column.
            return Vec3.createVectorHelper(posX, posY + Math.max(0.8D, captive.height * 0.5D + 0.1D), posZ);
        }
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
        if(isOcean()) { tickOceanCapture(); return; }
        captureTicks++;
        if(captive == null) { release("Lost entity reference"); return; }
        if(!captive.isEntityAlive() || captive.worldObj != worldObj || captive.noClip || !suitablePrey(captive)) {
            release("Invalid captive"); return;
        }
        if(!captive.addedToChunk || !GardenerNests.loaded(worldObj, captive.boundingBox.expand(1, 0, 1))) {
            release("Chunk unloading"); return;
        }
        if(worldObj.getEntityByID(captive.getEntityId()) != captive) { release("Lost entity reference"); return; }
        if(!getUniqueID().toString().equals(captive.getEntityData().getString("GardenerCaptor"))
                || captive.getEntityData().getLong("GardenerEscapeUntil") > worldObj.getTotalWorldTime()
                || captive instanceof EntityPlayer && (((EntityPlayer) captive).capabilities.disableDamage
                    || ((EntityPlayer) captive).capabilities.isCreativeMode)
                || preyDistanceSq(captive) > (attackReach() + 7D) * (attackReach() + 7D)
                    && !canTransportTo(Vec3.createVectorHelper(captive.posX, captive.boundingBox.minY + captive.height * 0.5D, captive.posZ))) {
            release("Victim escape"); return;
        }
        restrainCaptive();
        captureSneakTicks = captive instanceof EntityPlayer && captive.isSneaking() ? captureSneakTicks + 1 : 0;
        if(captureSneakTicks >= 20) { release("Victim escape"); return; }
        if(state() == CAPTURE) {
            if(attackTicks < 16) return;
            begin(pendingCocoon == null ? HOLD : LIFT);
        }
        if(pendingCocoon != null && (pendingCocoon.isDead || !pendingCocoon.attachmentValid())) {
            abandonCeiling("Invalid ceiling attachment");
        }
        // The appendage follows the live grip. Terrain along its straight visual skeleton
        // must not veto the captive's independent route out of a trench.
        double distance = captureDistance();
        if(distance < captureBestDistance - 0.25D) {
            captureBestDistance = distance; captureProgressTick = captureTicks;
        }
        if(pendingCocoon != null && state() == LIFT
                && captureTicks - captureProgressTick >= 160) {
            abandonCeiling("Capture stall timeout at ceiling; feeding fallback"); distance = captureDistance();
        }
        if(pendingCocoon != null) {
            if(state() == LIFT && distance <= 0.12D) {
                boolean attached = pendingCocoon.attach(this, captive);
                if(captive == null) return; // Protection/admission callbacks may interrupt ownership.
                if(attached) { movementObstructionTicks = 0; begin(ATTACH); }
                else if(pendingCocoon.placementObstructed()) { captureObstruction(); return; }
                else {
                    abandonCeiling("Cocoon placement rejection"); distance = captureDistance();
                }
            }
            if(state() == ATTACH && attackTicks >= 15 && distance <= 0.4D) begin(WRAP);
            if(state() == WRAP && distance <= 0.4D) {
                if(!pendingCocoon.fullyWrapped()) {
                    pendingCocoon.growWrapping();
                    captureProgressTick = captureTicks; // Actual wrapping, rather than positional jitter.
                }
                if(pendingCocoon.fullyWrapped()) {
                    if(pendingCocoon.seal(this, captive)) { release(false); return; }
                    if(captive == null) return;
                    if(pendingCocoon.placementObstructed()) { captureObstruction(); return; }
                    abandonCeiling("Cocoon placement rejection at sealing"); distance = captureDistance();
                }
            }
        }
        if(pendingCocoon == null && distance <= 1.25D && captureTicks - lastFeedingTick >= 20) {
            lastFeedingTick = captureTicks;
            Entity victim = captive;
            boolean fed, consumed;
            feeding = true; feedingDeath = null;
            try {
                fed = wound(victim, 4F);
                consumed = feedingDeath != null && !feedingDeath.isCanceled();
            } finally { feeding = false; feedingDeath = null; }
            if(captive != victim) return;
            if(fed) {
                captureProgressTick = captureTicks;
                playSound("mob.slime.attack", 0.8F, 0.75F);
            }
            if(!captive.isEntityAlive()) {
                if(fed && consumed) EntityGardenerCocoon.afterFeeding(this, captive);
                else EntityGardenerCocoon.failure(this, "feeding death does not qualify: damage=" + fed
                        + ", confirmed uncanceled death=" + consumed);
                release(false); return;
            }
        }
        boolean actionProgressing = pendingCocoon == null && distance <= 1.25D && captureTicks - captureProgressTick < 120
                || state() == WRAP && distance <= 0.4D;
        if(captureTicks - captureProgressTick >= (pendingCocoon == null ? 120 : 160)) {
            if(pendingCocoon != null) abandonCeiling("Capture stall timeout at attachment; feeding fallback");
            else if(distance > 1.25D) {
                // A detour may initially increase distance. Escalate transport, retaining the grip.
                captiveRoute = null; traversingBlocks = true;
            } else if(!actionProgressing) { release("Feeding made no progress"); return; }
        }
        moveCaptive();
    }
    private void moveCaptive() {
        Vec3 goal = captureGoal();
        Vec3 here = captiveCenter();
        if(routeGoal != null && routeGoal.distanceTo(goal) > 0.5D) resetTransport();
        // Prefer a clear point within bite range if terrain has covered the exact underside.
        if(pendingCocoon == null && !clearCaptiveSegment(goal, goal)) {
            Vec3 clear = clearNear(goal, 1);
            if(clear != null && clear.distanceTo(goal) <= 1.25D) goal = clear;
        }
        if(clearCaptiveSegment(here, goal)) {
            captiveRoute = null; traversingBlocks = false;
        } else if(captiveRoute == null && !traversingBlocks) {
            captiveRoute = findCaptiveRoute(here, goal);
            routeGoal = captureGoal();
            if(captiveRoute == null) {
                traversingBlocks = true;
                captureDiagnostic("No bounded clear prey route; controlled block traversal");
            }
        }
        while(captiveRoute != null && !captiveRoute.isEmpty() && here.distanceTo(captiveRoute.get(0)) <= 0.05D)
            captiveRoute.remove(0);
        Vec3 next = captiveRoute != null && !captiveRoute.isEmpty() ? captiveRoute.get(0) : goal;
        double distance = here.distanceTo(next);
        double scale = distance > 0 ? Math.min(0.24D / distance, 1D) : 0;
        Vec3 step = here.addVector((next.xCoord - here.xCoord) * scale,
                (next.yCoord - here.yCoord) * scale, (next.zCoord - here.zCoord) * scale);
        if(!loadedCaptiveSegment(here, step)) { release("Chunk unloading"); return; }
        if(!traversingBlocks && !clearCaptiveSegment(here, step)) {
            // Terrain changed after planning. Replan once, then traverse instead of retrying a wall.
            captiveRoute = null;
            if(++movementObstructionTicks >= 2) traversingBlocks = true;
            return;
        }
        // Collision-safe waypoints are preferred. The fallback bypasses blocks only for this
        // owned movement operation: no noClip flag can survive an interruption, save or release.
        positionCaptive(step);
        if(clearCaptiveSegment(step, step)) {
            safeCaptivePosition = step;
            captive.getEntityData().removeTag("GardenerDragSafe");
        } else if(safeCaptivePosition != null && !captive.getEntityData().hasKey("GardenerDragSafe")) {
            // Captures are intentionally transient across saves. An orphan loaded mid-wall must
            // return to its last clear grip before resuming ordinary movement.
            NBTTagCompound safe = new NBTTagCompound();
            safe.setDouble("X", safeCaptivePosition.xCoord); safe.setDouble("Y", safeCaptivePosition.yCoord);
            safe.setDouble("Z", safeCaptivePosition.zCoord); captive.getEntityData().setTag("GardenerDragSafe", safe);
        }
        if(distance > 0.05D && here.distanceTo(captiveCenter()) >= 0.01D) captureProgressTick = captureTicks;
        captive.motionX = captive.motionY = captive.motionZ = 0; captive.fallDistance = 0;
        syncHeldPlayer();
        // The normal entity tracker sends position deltas; zero-velocity packets each tick add no support.
    }
    private void captureObstruction() {
        // Only cocoon placement occupancy uses this retry budget; transport never releases on terrain.
        if(++movementObstructionTicks >= 40) abandonCeiling("Cocoon occupancy; feeding fallback");
    }
    private void resetTransport() {
        captiveRoute = null; routeGoal = null; traversingBlocks = false; movementObstructionTicks = 0;
    }
    private Vec3 captiveCenter() {
        return Vec3.createVectorHelper(captive.posX, captive.boundingBox.minY + captive.height * 0.5D, captive.posZ);
    }
    private void positionCaptive(Vec3 center) {
        // Preserve the native feet/origin offset, including players' yOffset.
        double offset = captive.posY - captive.boundingBox.minY;
        captive.setPosition(center.xCoord, center.yCoord - captive.height * 0.5D + offset, center.zCoord);
    }
    private AxisAlignedBB captiveSweep(Vec3 from, Vec3 to) {
        AxisAlignedBB box = captive.boundingBox.getOffsetBoundingBox(from.xCoord - captive.posX,
                from.yCoord - captive.boundingBox.minY - captive.height * 0.5D, from.zCoord - captive.posZ);
        return box.addCoord(to.xCoord - from.xCoord, to.yCoord - from.yCoord, to.zCoord - from.zCoord);
    }
    private boolean loadedCaptiveSegment(Vec3 from, Vec3 to) {
        AxisAlignedBB box = captiveSweep(from, to);
        return box.minY >= 0 && box.maxY <= worldObj.getActualHeight() && GardenerNests.loaded(worldObj, box.expand(1, 0, 1));
    }
    private boolean clearCaptiveSegment(Vec3 from, Vec3 to) {
        // Block shapes only: neither the large parent AABB nor its own appendages obstruct feeding.
        return loadedCaptiveSegment(from, to) && worldObj.func_147461_a(captiveSweep(from, to)).isEmpty();
    }
    /** Bounded six-axis A* for the held volume, independent of the victim's walking AI. */
    private List<Vec3> findCaptiveRoute(Vec3 start, Vec3 goal) {
        PriorityQueue<CaptiveStep> open = new PriorityQueue<CaptiveStep>();
        Set<ChunkCoordinates> visited = new HashSet<ChunkCoordinates>();
        open.add(new CaptiveStep(0, 0, 0, start, null, goal));
        int[][] directions = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
        for(int examined = 0; !open.isEmpty() && examined < 768; examined++) {
            CaptiveStep node = open.poll();
            if(!visited.add(new ChunkCoordinates(node.x, node.y, node.z))) continue;
            if(node.point.distanceTo(goal) <= 1.75D && clearCaptiveSegment(node.point, goal)) {
                List<Vec3> route = new ArrayList<Vec3>(); route.add(goal);
                for(CaptiveStep part = node; part.parent != null; part = part.parent) route.add(0, part.point);
                return route;
            }
            for(int[] direction : directions) {
                int x = node.x + direction[0], y = node.y + direction[1], z = node.z + direction[2];
                if(visited.contains(new ChunkCoordinates(x, y, z))) continue;
                Vec3 point = start.addVector(x, y, z);
                if(point.xCoord < Math.min(start.xCoord, goal.xCoord) - 4 || point.xCoord > Math.max(start.xCoord, goal.xCoord) + 4
                        || point.zCoord < Math.min(start.zCoord, goal.zCoord) - 4 || point.zCoord > Math.max(start.zCoord, goal.zCoord) + 4
                        || point.yCoord < Math.min(start.yCoord, goal.yCoord) - 2 || point.yCoord > Math.max(start.yCoord, goal.yCoord) + 6
                        || node.cost > start.distanceTo(goal) + 18 || !clearCaptiveSegment(node.point, point)) continue;
                open.add(new CaptiveStep(x, y, z, point, node, goal));
            }
        }
        return null;
    }
    private static final class CaptiveStep implements Comparable<CaptiveStep> {
        final int x, y, z, cost;
        final double score;
        final Vec3 point;
        final CaptiveStep parent;
        CaptiveStep(int x, int y, int z, Vec3 point, CaptiveStep parent, Vec3 goal) {
            this.x = x; this.y = y; this.z = z; this.point = point; this.parent = parent;
            cost = parent == null ? 0 : parent.cost + 1; score = cost + point.distanceTo(goal);
        }
        @Override public int compareTo(CaptiveStep other) { return Double.compare(score, other.score); }
    }
    private Vec3 clearNear(Vec3 center, int radius) {
        if(clearCaptiveSegment(center, center)) return center;
        Vec3 best = null;
        double distance = Double.MAX_VALUE;
        for(int y = radius; y >= -radius; y--) for(int x = -radius; x <= radius; x++) for(int z = -radius; z <= radius; z++) {
            double squared = x * x + y * y + z * z;
            if(squared >= distance) continue;
            Vec3 point = center.addVector(x, y, z);
            if(clearCaptiveSegment(point, point)) { best = point; distance = squared; }
        }
        return best;
    }
    private void restoreCaptivePosition() {
        if(captive.worldObj != worldObj || captive.isDead || clearCaptiveSegment(captiveCenter(), captiveCenter())) return;
        // Roll back to the last clear grip before restoring AI, even on escape/death/unload.
        Vec3 safe = safeCaptivePosition == null ? null : clearNear(safeCaptivePosition, 2);
        if(safe == null) safe = clearNear(captiveCenter(), 4);
        if(safe != null) {
            positionCaptive(safe);
            if(captive instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) captive;
                if(player.playerNetServerHandler != null) player.playerNetServerHandler.setPlayerLocation(
                        player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch);
            }
        }
    }
    private void abandonCeiling(String reason) {
        captureDiagnostic(reason);
        if(pendingCocoon != null) pendingCocoon.cancelWrapping();
        pendingCocoon = null;
        dataWatcher.updateObject(31, new ChunkCoordinates(0, -1, 0));
        begin(HOLD);
        resetTransport();
        captureBestDistance = captureDistance(); captureProgressTick = lastFeedingTick = captureTicks;
    }
    /** Ocean capture retains its original movement, damage, escape and timeout behavior. */
    private void tickOceanCapture() {
        captureTicks++;
        if(captive != null && !captive.isEntityAlive()) { release(false); return; }
        if(captive == null || !prey(captive) || worldObj.getEntityByID(captive.getEntityId()) != captive
                || preyDistanceSq(captive) > (attackReach() + 2D) * (attackReach() + 2D)
                || !GardenerNests.loaded(worldObj, captive.boundingBox.expand(1, 0, 1))) {
            release("Invalid captive"); return;
        }
        if(!clearLimb(attackArm())) { release("Tentacle obstruction"); return; }
        aim(captive, false);
        captureSneakTicks = captive instanceof EntityPlayer && captive.isSneaking() ? captureSneakTicks + 1 : 0;
        if(captureSneakTicks >= 20) { release("Victim escape"); return; }
        double distance = captureDistance();
        if(distance <= 1.25D && captureTicks - lastFeedingTick >= 20) {
            lastFeedingTick = captureTicks;
            if(wound(captive, 8F)) {
                captureProgressTick = captureTicks;
                playSound("mob.slime.attack", 0.8F, 0.6F);
            }
            if(!captive.isEntityAlive()) { release(false); return; }
        }
        if(distance < captureBestDistance - 0.25D) {
            captureBestDistance = distance; captureProgressTick = captureTicks;
        }
        if(captureTicks >= 1200 || captureTicks - captureProgressTick >= 120) { release("Capture timeout"); return; }
        Vec3 goal = captureGoal();
        double dx = goal.xCoord - captive.posX, dz = goal.zCoord - captive.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double scale = horizontal > 0 ? Math.min(0.24D / horizontal, 0.12D) : 0;
        double vy = MathHelper.clamp_double((goal.yCoord - captive.boundingBox.minY - captive.height * 0.5D) * 0.12D, -0.2D, 0.36D);
        if(!GardenerNests.loaded(worldObj, captive.boundingBox.addCoord(dx * scale, vy, dz * scale).expand(1, 0, 1))) {
            release("Chunk unloading"); return;
        }
        captive.moveEntity(dx * scale, vy, dz * scale);
        captive.motionX = captive.motionY = captive.motionZ = 0; captive.fallDistance = 0;
        syncHeldPlayer();
        captive.velocityChanged = true;
    }
    private void restrainCaptive() {
        if(captive instanceof EntityLiving) {
            if(heldNavigation == null) heldNavigation = new HeldNavigation((EntityLiving) captive);
            heldNavigation.suspend();
        }
        if(captive instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) captive;
            living.moveForward = living.moveStrafing = 0;
            living.setJumping(false);
        }
        // The tentacle supports the real entity. No persistent collision or gravity flags.
        captive.motionX = captive.motionY = captive.motionZ = 0;
        captive.fallDistance = 0;
    }
    /** Called before native living AI/physics; stale saved ownership never immobilizes a reloaded victim. */
    public static void updateCaptive(EntityLivingBase victim) {
        updateCaptiveMovement(victim);
    }
    public static boolean updateCaptiveMovement(EntityLivingBase victim) {
        if(victim.worldObj.isRemote || !victim.getEntityData().hasKey("GardenerCaptor")) return false;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("GardenerCaptorId"));
        if(owner instanceof EntityGardener && owner.getUniqueID().toString().equals(victim.getEntityData().getString("GardenerCaptor"))
                && ((EntityGardener) owner).isHolding(victim)) {
            EntityGardener gardener = (EntityGardener) owner;
            if(!gardener.isOcean()) {
                if(!gardener.active()) gardener.release("Gardener deactivation");
                else { gardener.restrainCaptive(); return true; }
            }
        } else {
            restoreSavedCaptive(victim);
            victim.getEntityData().removeTag("GardenerCaptor"); victim.getEntityData().removeTag("GardenerCaptorId");
        }
        return false;
    }
    private static void restoreSavedCaptive(EntityLivingBase victim) {
        if(!victim.getEntityData().hasKey("GardenerDragSafe")) return;
        NBTTagCompound safe = victim.getEntityData().getCompoundTag("GardenerDragSafe");
        double x = safe.getDouble("X"), y = safe.getDouble("Y"), z = safe.getDouble("Z");
        AxisAlignedBB box = victim.boundingBox.getOffsetBoundingBox(x - victim.posX,
                y - victim.boundingBox.minY - victim.height * 0.5D, z - victim.posZ);
        if(GardenerNests.loaded(victim.worldObj, box.expand(1, 0, 1)) && victim.worldObj.func_147461_a(box).isEmpty())
            victim.setPosition(x, y - victim.height * 0.5D + victim.posY - victim.boundingBox.minY, z);
        victim.getEntityData().removeTag("GardenerDragSafe");
    }
    /** Suffocation alone is suppressed while an owned grip is traversing terrain. */
    public static boolean preventsDraggingSuffocation(EntityLivingBase victim) {
        if(victim.worldObj.isRemote || !victim.getEntityData().hasKey("GardenerCaptor")) return false;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("GardenerCaptorId"));
        if(!(owner instanceof EntityGardener)) return false;
        EntityGardener gardener = (EntityGardener) owner;
        return !gardener.isOcean() && gardener.traversingBlocks && gardener.isHolding(victim)
                && gardener.getUniqueID().toString().equals(victim.getEntityData().getString("GardenerCaptor"));
    }
    /** Only the held mob's task instances/path are suspended; constructors/NBT remain unchanged. */
    private static final class HeldNavigation {
        private static final java.lang.reflect.Field SPEED = cpw.mods.fml.relauncher.ReflectionHelper.findField(
                net.minecraft.pathfinding.PathNavigate.class, "speed", "field_75511_d");
        private final EntityLiving mob;
        private final World world;
        private final net.minecraft.pathfinding.PathEntity path;
        private final double speed;
        private final List<net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry> tasks = new ArrayList<net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry>();
        private final List<net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry> targets = new ArrayList<net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry>();
        HeldNavigation(EntityLiving mob) {
            this.mob = mob; world = mob.worldObj; path = mob.getNavigator().getPath();
            try { speed = SPEED.getDouble(mob.getNavigator()); }
            catch(IllegalAccessException ex) { throw new IllegalStateException("Cannot suspend Gardener prey navigation", ex); }
        }
        void suspend() {
            suspend(mob.tasks, tasks); suspend(mob.targetTasks, targets);
            mob.getNavigator().clearPathEntity();
            mob.getMoveHelper().setMoveTo(mob.posX, mob.posY, mob.posZ, 0);
            mob.getJumpHelper().doJump(); mob.setJumping(false);
        }
        private static void suspend(net.minecraft.entity.ai.EntityAITasks scheduler,
                List<net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry> saved) {
            if(scheduler.taskEntries.isEmpty()) return;
            for(Object entry : new ArrayList<Object>(scheduler.taskEntries)) {
                net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry task = (net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry) entry;
                if(!saved.contains(task)) saved.add(task);
                scheduler.removeTask(task.action);
            }
        }
        private static void restore(net.minecraft.entity.ai.EntityAITasks scheduler,
                List<net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry> saved) {
            for(net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry task : saved) {
                boolean present = false;
                for(Object entry : scheduler.taskEntries)
                    if(((net.minecraft.entity.ai.EntityAITasks.EntityAITaskEntry) entry).action == task.action) present = true;
                if(!present) scheduler.addTask(task.priority, task.action);
            }
            saved.clear();
        }
        void restore() {
            restore(mob.tasks, tasks); restore(mob.targetTasks, targets);
            if(mob.worldObj == world && mob.isEntityAlive()) mob.getNavigator().setPath(path, speed);
        }
    }
    public void recordFeedingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if(feeding && captive == event.entityLiving && event.source.getEntity() == this) feedingDeath = event;
    }
    void queueSurfaceCocoon(Entity victim) {
        if(worldObj.isRemote || isOcean() || placingSurface) return;
        for(SurfaceBiomass biomass : surfaceBiomass) if(biomass.id.equals(victim.getUniqueID())) return;
        // Feeding has already completed. Rejected output is neither a placement nor pending biomass.
        if(!EntityGardenerCocoon.admission(this) || surfaceBiomass.size() >= 16) return;
        SurfaceBiomass biomass = new SurfaceBiomass(victim);
        surfaceBiomass.add(biomass);
        placeSurfaceBiomass(biomass);
    }
    private void tickSurfaceBiomass() {
        if(!surfaceBiomass.isEmpty() && ticksExisted % 20 == 0) placeSurfaceBiomass(surfaceBiomass.get(0));
    }
    private void placeSurfaceBiomass(SurfaceBiomass biomass) {
        if(placingSurface || !active()) return;
        if(!EntityGardenerCocoon.admission(this)) { surfaceBiomass.remove(biomass); return; }
        placingSurface = true;
        try {
            boolean diagnose = !biomass.attempted || --biomass.remaining <= 0;
            if(diagnose) biomass.remaining = 60;
            boolean placed = EntityGardenerCocoon.placeSurface(this, biomass.victim, biomass.id,
                    biomass.x, biomass.y, biomass.z, diagnose);
            biomass.attempted = true;
            if(placed) {
                surfaceBiomass.remove(biomass);
            } else {
                surfaceBiomass.remove(biomass);
                // Admission may have changed inside cancelable placement/spawn callbacks.
                if(EntityGardenerCocoon.admission(this)) surfaceBiomass.add(biomass);
            }
        } finally { placingSurface = false; }
    }
    void reportCocoonAdmission(String reason) {
        if(reason != null && !reason.equals(cocoonAdmissionFailure)) EntityGardenerCocoon.failure(this, reason);
        cocoonAdmissionFailure = reason;
    }
    private static final class SurfaceBiomass {
        final UUID id;
        final double x, y, z;
        final Entity victim;
        int remaining = 60;
        boolean attempted;
        SurfaceBiomass(Entity victim) {
            this.victim = victim; id = victim.getUniqueID(); x = victim.posX; y = victim.boundingBox.minY; z = victim.posZ;
        }
        SurfaceBiomass(NBTTagCompound tag) {
            victim = null; id = UUID.fromString(tag.getString("Victim"));
            x = tag.getDouble("X"); y = tag.getDouble("Y"); z = tag.getDouble("Z");
            remaining = MathHelper.clamp_int(tag.getInteger("Remaining"), 1, 1200); attempted = tag.getBoolean("Attempted");
        }
        NBTTagCompound save() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Victim", id.toString());
            tag.setDouble("X", x); tag.setDouble("Y", y); tag.setDouble("Z", z);
            tag.setInteger("Remaining", remaining); tag.setBoolean("Attempted", attempted); return tag;
        }
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
    private void syncHeldPlayer() {
        if(captive instanceof net.minecraft.entity.player.EntityPlayerMP) {
            net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) captive;
            HeldPlayer.support(player);
            if(attackTicks % 2 == 0 && player.playerNetServerHandler != null)
                player.playerNetServerHandler.setPlayerLocation(captive.posX, captive.posY, captive.posZ, captive.rotationYaw, captive.rotationPitch);
        }
    }
    public boolean isHolding(Entity entity) {
        return entity != null && captive == entity && entity.worldObj == worldObj && captureState() && isEntityAlive();
    }
    boolean isPreparingCocoon(EntityGardenerCocoon cocoon, Entity victim) {
        return pendingCocoon == cocoon && isHolding(victim) && (state() == ATTACH || state() == WRAP);
    }
    public void releaseForChunkUnload(int x, int z) {
        if(chunkCoordX == x && chunkCoordZ == z || captive != null && captive.chunkCoordX == x && captive.chunkCoordZ == z) release("Chunk unloading");
    }
    public void releaseForWorldUnload() { release("Chunk unloading: world unload"); }
    /** Short collision-tested steering probes instead of an enormous vanilla cave path search. */
    private void tickLocomotion(Entity target) {
        if(captive != null || state() != IDLE || !isOcean() && target != null && tipInReach(target) && reach(target, attackReach())) {
            if(!isOcean()) travelX = travelY = travelZ = 0;
            dataWatcher.updateObject(29, 0F); return;
        }
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
            if(attackArm() == arm) release("Disabled tentacle");
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
    @Override public void onDeath(DamageSource source) { if(!worldObj.isRemote) { release("Gardener deactivation: death"); removeParts(); } super.onDeath(source); }
    @Override public void setDead() { if(!worldObj.isRemote) { release("Gardener deactivation: removed"); removeParts(); } super.setDead(); }
    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setBoolean("NestAnchored", anchored); nbt.setDouble("NestX", nestX); nbt.setDouble("NestY", nestY); nbt.setDouble("NestZ", nestZ);
        nbt.setInteger("Summons", summons); nbt.setInteger("SummonTicks", summonTicks); nbt.setInteger("Cooldown", cooldown);
        nbt.setInteger("GardenerFormVersion", 2);
        NBTTagList biomass = new NBTTagList();
        for(SurfaceBiomass entry : surfaceBiomass) biomass.appendTag(entry.save());
        nbt.setTag("SurfaceBiomass", biomass);
        for(int i = 0; i < 6; i++) { nbt.setFloat("LimbHealth" + i, limbHealth[i]); nbt.setInteger("LimbRecovery" + i, recovery[i]); }
        // Captures and in-flight attacks end on reload. Saved claim hints are valid only with a live holding owner.
    }
    @Override public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt); fromSave = true;
        if(nbt.getInteger("GardenerFormVersion") < 2 && !isOcean()
                && getEntityAttribute(SharedMonsterAttributes.movementSpeed).getBaseValue() == 0D)
            getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.08D);
        anchored = nbt.getBoolean("NestAnchored"); nestX = nbt.getDouble("NestX"); nestY = nbt.getDouble("NestY"); nestZ = nbt.getDouble("NestZ");
        summons = MathHelper.clamp_int(nbt.getInteger("Summons"), 0, 16); summonTicks = MathHelper.clamp_int(nbt.getInteger("SummonTicks"), 0, 400);
        cooldown = Math.max(80, MathHelper.clamp_int(nbt.getInteger("Cooldown"), 0, 400));
        surfaceBiomass.clear();
        NBTTagList biomass = nbt.getTagList("SurfaceBiomass", 10);
        if(!isOcean()) for(int i = 0; i < Math.min(16, biomass.tagCount()); i++) {
            try { surfaceBiomass.add(new SurfaceBiomass(biomass.getCompoundTagAt(i))); }
            catch(IllegalArgumentException ignored) { }
        }
        for(int i = 0; i < 6; i++) {
            limbHealth[i] = nbt.hasKey("LimbHealth" + i) ? MathHelper.clamp_float(nbt.getFloat("LimbHealth" + i), 0, 32) : 32;
            recovery[i] = MathHelper.clamp_int(nbt.getInteger("LimbRecovery" + i), 0, 300);
            if(limbHealth[i] <= 0 && recovery[i] == 0) recovery[i] = 300;
        }
        captive = null; begin(IDLE);
    }
}
