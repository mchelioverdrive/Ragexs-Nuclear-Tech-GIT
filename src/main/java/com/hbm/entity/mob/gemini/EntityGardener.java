package com.hbm.entity.mob.gemini;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.hbm.config.GeminiConfig;
import com.hbm.main.MainRegistry;
import com.hbm.handler.GeminiCaptures;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import com.hbm.world.gemini.GeminiInvasionManager;
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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A nest-bound miniboss. Captives stay real entities with server-owned transport. */
public class EntityGardener extends EntityLiving implements IMob, IBossDisplayData, IGeminiCreature {
    public static final float BODY_WIDTH = 5.8F, BODY_HEIGHT = 8F;
    public static final int IDLE = 0, REACH = 1, HOLD = 2, SLAM = 3, RECOVER = 4;
    public static final int CAPTURE = 5, LIFT = 6, ATTACH = 7, WRAP = 8, RELEASE = 9;
    private final EntityGardenerTentacle[] parts = new EntityGardenerTentacle[GardenerPose.ARMS * GardenerPose.SEGMENTS];
    private final float[] limbHealth = {32, 32, 32, 32, 32, 32};
    private final int[] recovery = new int[6], limbHurt = new int[6];
    private final Set<UUID> struck = new HashSet<UUID>();
    private static final int CAPTURE_SLOTS = 3;
    private final CaptureSlot[] captures = {new CaptureSlot(0), new CaptureSlot(1), new CaptureSlot(2)};
    private Entity preyTarget;
    private final List<SurfaceBiomass> surfaceBiomass = new ArrayList<SurfaceBiomass>();
    private boolean placingSurface;
    private String cocoonAdmissionFailure;
    protected double nestX, nestY, nestZ;
    private boolean sharedNest;
    private double sharedX, sharedZ;
    private double patrolX, patrolZ;
    private final GardenerBurrowing burrowing = new GardenerBurrowing(this);
    private final GardenerSurfaceHunting surfaceHunting = new GardenerSurfaceHunting(this);
    private final GardenerLocomotion locomotion = new GardenerLocomotion(this);
    private boolean traversalPose;
    private double travelX, travelY, travelZ;
    private float previousCrawl, crawl;
    private boolean anchored, fromSave;
    private int cooldown = 60, attackTicks, summons, summonTicks;
    private int smashCursor = -1, smashX, smashY, smashZ, smashRemoved;
    private static final boolean CAPTURE_DIAGNOSTICS = Boolean.getBoolean("hbm.gardenerCaptureDiagnostics");
    private long nextCaptureDiagnostic;

    public EntityGardener(World world) {
        super(world); setSize(BODY_WIDTH, BODY_HEIGHT); stepHeight = 1F; experienceValue = 80; func_110163_bv();
        ignoreFrustumCheck = true; setHealth(getMaxHealth());
    }
    @Override public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        GeminiSpawning.initialize(this);
        return super.onSpawnWithEgg(data);
    }
    @Override protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(19, 0); // Underground locomotion mode, wall heading and gait checkpoint.
        dataWatcher.addObject(20, -1); // Eye target
        dataWatcher.addObject(21, (byte) IDLE);
        dataWatcher.addObject(22, 0); // State start in world ticks, synchronized without a packet per frame
        dataWatcher.addObject(23, (byte) 0);
        dataWatcher.addObject(24, 0F); dataWatcher.addObject(25, 0F); dataWatcher.addObject(26, 0F);
        dataWatcher.addObject(27, (byte) 0); // Disabled limb mask
        dataWatcher.addObject(28, (byte) 0); // Server-owned activity, also freezes dormant presentation
        dataWatcher.addObject(29, 0F); // Locomotion intensity, interpolated by the client
        dataWatcher.addObject(30, 0xFFFFFF); // Six four-bit underground limb extensions
        dataWatcher.addObject(31, ""); // Event-driven captive/arm snapshots; positions use native tracking.
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
    protected double territoryRadius() { return burrowing.surface() ? GeminiConfig.gardenerHuntingRadius + 48D : sharedNest ? 90D : 22D; }
    boolean hasSharedNest() { return sharedNest && !isOcean(); }
    boolean pursuingPrey() { return prey(preyTarget) && freeCapture() != null; }
    boolean surfaceHunter() { return !isOcean() && burrowing.surface(); }
    boolean surfaceCaptureReach(Entity target) { return tipInReach(target) && reach(target, attackReach()); }
    boolean canSeeSurfacePrey(Entity target) {
        return GardenerNests.loaded(worldObj, boundingBox.addCoord(target.posX - posX, target.posY - posY, target.posZ - posZ))
                && getEntitySenses().canSee(target);
    }
    double huntingX() { return burrowing.patrolX(); }
    double huntingZ() { return burrowing.patrolZ(); }
    double huntingHomeX() { return hasSharedNest() ? sharedX : nestX; }
    double huntingHomeZ() { return hasSharedNest() ? sharedZ : nestZ; }
    int surfaceTravel(double x, double y, double z) { return surfaceHunting.travel(x, y, z, 0.12D, 1.3D); }
    Vec3 surfaceApproach(int x, int z, double y, double range) { return surfaceHunting.approach(x, z, y, range); }
    boolean gardenerFooting(double x, double y, double z) { return surfaceHunting.supported(x, y, z); }
    boolean gardenerSeparation(double x, double y, double z, boolean levelCorrection) {
        return surfaceHunting.separating(x, y, z, levelCorrection);
    }
    void stopSurfaceTravel() { surfaceHunting.stop(); }
    void stopSurfaceTravel(String reason) { surfaceHunting.stop(reason); }
    void emergedOnSurface() { surfaceHunting.emerged(); }
    boolean crossingSurfaceEdge() { return surfaceHunting.crossingEdge(); }
    Vec3 surfaceHuntingDestination() {
        Vec3 approach = burrowing.approachDestination();
        return approach == null ? surfaceHunting.destination() : approach;
    }
    String surfaceMovementState() { return burrowing.movementState(); }
    String nestNavigationContext() { return burrowing.diagnosticContext(); }
    boolean huntingDebugEnabled() { return GeminiInvasionManager.debugging(worldObj); }
    void huntingDiagnostic(String event, String message) {
        if(!isOcean()) GeminiInvasionManager.debug(worldObj,
                "gardener-" + getUniqueID() + "-" + event, "Gardener " + getEntityId() + ": " + message);
    }
    boolean readyToCarry() {
        for(CaptureSlot slot : captures) if(slot.victim() != null && !slot.attached) return false;
        return true;
    }
    private boolean hasHuntingMeal() {
        for(CaptureSlot slot : captures) if(slot.returningMeal && slot.victim() != null) return true;
        return false;
    }
    public void assignNest(double x, double y, double z, double homeX, double homeZ, boolean shared, boolean relocate) {
        if(isOcean()) return;
        boolean changed = nestX != homeX || nestY != y || nestZ != homeZ;
        sharedNest = shared; sharedX = x; sharedZ = z;
        nestX = homeX; nestY = y; nestZ = homeZ; anchored = true;
        if(changed && !relocate) burrowing.resetHome();
        if(relocate) { setPosition(homeX, y, homeZ); lastTickPosX = posX; lastTickPosY = posY; lastTickPosZ = posZ; }
    }
    protected boolean environmentActive() {
        return GeminiSpawning.manualAdmission(this) || GeminiSpawning.structureDebugAdmission(this) || burrowing.enabled() && GardenerNests.active(worldObj)
                || nestY >= 8 && nestY <= 44 && GardenerNests.active(worldObj)
                && worldObj.blockExists(MathHelper.floor_double(nestX), MathHelper.floor_double(nestY + 9), MathHelper.floor_double(nestZ))
                && !worldObj.canBlockSeeTheSky(MathHelper.floor_double(nestX), MathHelper.floor_double(nestY + 9), MathHelper.floor_double(nestZ));
    }
    public float crawlAmount(float partial) { return previousCrawl + (crawl - previousCrawl) * partial; }
    public double bodyShift(float partial) {
        return (isAwake() ? Math.sin((worldObj.getTotalWorldTime() + partial) * 0.026D) * 0.12D : 0)
                + (isOcean() ? -crawlAmount(partial) * 0.12D : locomotion.bodyBob(partial));
    }
    public double climbPitch(float partial) { return isOcean() ? 0 : locomotion.pitch(partial); }
    public double climbX(float partial) { return locomotion.tiltX(partial); }
    public double climbZ(float partial) { return locomotion.tiltZ(partial); }
    Vec3 locomotionJoint(int arm, double t, double x, double y, double z, float partial, double extension) {
        return locomotion.joint(arm, t, x, y, z, partial, extension);
    }
    void traversalPose(int mode, double x, double z) {
        traversalPose = true;
        locomotion.command(mode, x, z);
    }
    private void updateLocomotion(double x, double y, double z, int defaultMode) {
        if(!traversalPose) locomotion.command(defaultMode, 0, 0);
        locomotion.advance(posX - x, posY - y, posZ - z);
        dataWatcher.updateObject(19, locomotion.snapshot(dataWatcher.getWatchableObjectInt(19)));
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
    public boolean captureState() {
        for(CaptureSlot slot : captures) if(slot.victim() != null) return true;
        return false;
    }
    public boolean hangingCapture() {
        for(CaptureSlot slot : captures) if(slot.victim() != null && slot.selectedAttachment.posY >= 0) return true;
        return false;
    }
    public ChunkCoordinates cocoonAttachment() {
        for(CaptureSlot slot : captures) if(slot.victim() != null) return new ChunkCoordinates(slot.selectedAttachment);
        return new ChunkCoordinates(0, -1, 0);
    }
    @Override public void func_145781_i(int id) {
        super.func_145781_i(id);
        if(id == 31 && worldObj.isRemote && captures != null) {
            String[] entries = dataWatcher.getWatchableObjectString(31).split(";", -1);
            for(int i = 0; i < captures.length; i++) captures[i].readSnapshot(i < entries.length ? entries[i] : "");
        }
    }
    private CaptureSlot captureFor(Entity victim) {
        if(victim != null) for(CaptureSlot slot : captures) if(slot.captive == victim) return slot;
        return null;
    }
    private CaptureSlot captureForArm(int arm) {
        for(CaptureSlot slot : captures) if(slot.arm == arm && slot.state != IDLE) return slot;
        return null;
    }
    private CaptureSlot freeCapture() {
        for(CaptureSlot slot : captures) if(slot.state == IDLE) return slot;
        return null;
    }
    public int armState(int arm) {
        CaptureSlot slot = captureForArm(arm);
        if(slot != null) return slot.state == RELEASE || slot.victim() != null ? slot.state : IDLE;
        return arm == attackArm() ? state() : IDLE;
    }
    public float armStateTime(int arm, float partial) {
        CaptureSlot slot = captureForArm(arm);
        return slot == null ? stateTime(partial) : Math.max(0, (int) worldObj.getTotalWorldTime() - slot.stateStart + partial);
    }
    public Entity armCaptive(int arm) {
        CaptureSlot slot = captureForArm(arm);
        return slot == null ? null : slot.victim();
    }
    public static boolean holdingState(int state) { return state == HOLD || state >= CAPTURE && state <= WRAP; }
    public Vec3 limbAim(float partial) { return limbAim(attackArm(), partial); }
    public Vec3 limbAim(int arm, float partial) {
        CaptureSlot slot = captureForArm(arm);
        Entity target = slot == null ? eyeTarget() : slot.victim();
        int pose = armState(arm);
        if(target != null && (holdingState(pose) || pose == REACH)) {
            double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial
                    - lastTickPosX - (posX - lastTickPosX) * partial;
            double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial
                    + target.boundingBox.minY - target.posY + target.height * 0.5D
                    - lastTickPosY - (posY - lastTickPosY) * partial;
            double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial
                    - lastTickPosZ - (posZ - lastTickPosZ) * partial;
            if(holdingState(pose)) return Vec3.createVectorHelper(x,
                    y + (isOcean() ? 0 : gripHeight(target) - target.height * 0.5D), z);
            if(!isOcean()) y += gripHeight(target) - target.height * 0.5D;
            double length = Math.sqrt(x * x + z * z), scale = length > attackReach() ? attackReach() / length : 1D;
            return Vec3.createVectorHelper(x * scale, MathHelper.clamp_double(y,
                    bodyOffset() + (isOcean() ? -28 : -8), bodyOffset() + (isOcean() ? 31 : 8)), z * scale);
        }
        return slot == null ? Vec3.createVectorHelper(aimX(), aimY(), aimZ()) : slot.releaseAim;
    }
    private void syncCaptures() {
        if(worldObj.isRemote) return;
        StringBuilder snapshot = new StringBuilder();
        for(CaptureSlot slot : captures) { if(slot.index > 0) snapshot.append(';'); snapshot.append(slot.snapshot()); }
        dataWatcher.updateObject(31, snapshot.toString());
    }
    private void release(String reason) { for(CaptureSlot slot : captures) if(slot.state != IDLE) slot.release(reason); }

    public boolean armDisabled(int arm) { return arm < 0 || arm >= 6 || (dataWatcher.getWatchableObjectByte(27) & (1 << arm)) != 0; }
    public Entity eyeTarget() {
        Entity entity = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(20));
        if(entity != null && entity.isEntityAlive()) return entity;
        for(CaptureSlot slot : captures) {
            Entity victim = slot.victim();
            if(victim != null) return victim;
        }
        return null;
    }
    public void anchor(double x, double y, double z) {
        nestX = x; nestY = y; nestZ = z; anchored = true;
        setPosition(x, y, z);
    }
    public void setHuntingGround(double x, double z) { if(!isOcean()) burrowing.huntingGround(x, z); }
    private boolean active() {
        return isEntityAlive() && anchored && GardenerNests.loaded(worldObj, activityBounds()) && environmentActive();
    }
    private AxisAlignedBB activityBounds() {
        if(GeminiSpawning.isManual(this)) return boundingBox;
        // Each surface/commute step checks its own loaded sweep. An unloaded chunk
        // under a decorative limb must not stop the entire body at the boundary.
        return !isOcean() && burrowing.travelling() ? boundingBox : visualBounds();
    }
    public AxisAlignedBB visualBounds() {
        return boundingBox.expand(isOcean() ? 32 : 18, isOcean() ? 32 : 2, isOcean() ? 32 : 18);
    }
    public boolean hasSpawnSpace() { return spawnRejection() == null; }
    public String spawnRejection() {
        boolean manual = GeminiSpawning.isManual(this);
        if(!GardenerNests.loaded(worldObj, manual ? boundingBox : visualBounds())) return "unloaded body/visual bounds";
        if(!worldObj.checkNoEntityCollision(boundingBox)) return "entity occupies resident body";
        if(!worldObj.getCollidingBoundingBoxes(this, boundingBox).isEmpty()) return "body collision";
        if(worldObj.isAnyLiquid(boundingBox)) return "liquid in resident body";
        if(manual) return null;
        if(!isOcean()) {
            AxisAlignedBB envelope = AxisAlignedBB.getBoundingBox(posX - 15, posY + 0.05D, posZ - 15, posX + 15, posY + 11, posZ + 15);
            if(!worldObj.func_147461_a(envelope).isEmpty() || worldObj.isAnyLiquid(envelope)) return "full extended model/limb terrain clearance";
            for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, envelope)) {
                Entity other = (Entity) entry;
                if(other instanceof EntityLivingBase && other.isEntityAlive()) return "living entity occupies resident limb envelope";
            }
        }
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) if(!clearLimb(arm)) return "limb " + arm + " obstructed";
        return null;
    }
    protected boolean inTerritory(Entity target) {
        double dx = target.posX - nestX, dz = target.posZ - nestZ;
        if(surfaceHunter()) { dx = target.posX - huntingHomeX(); dz = target.posZ - huntingHomeZ(); }
        if(hasSharedNest() && !burrowing.surface()) return GardenerNests.inHabitat(target.posX - sharedX, target.posZ - sharedZ, 0)
                && target.posY >= nestY - 2 && target.posY <= nestY + 24;
        return dx * dx + dz * dz <= territoryRadius() * territoryRadius()
                && Math.abs(target.posY - (burrowing.surface() ? posY : nestY)) < (isOcean() ? 32D : burrowing.surface() ? 16D : 8D);
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
    private String preyRejection(Entity target) { return preyRejection(target, true); }
    private String preyRejection(Entity target, boolean acquiring) {
        String rejection = preyEligibility(target, acquiring);
        if(rejection != null) return rejection;
        if(!inTerritory(target)) return "Outside territory";
        double search = surfaceHunter() ? (target == preyTarget ? 80D : 48D) : attackReach() + 7D;
        if(preyDistanceSq(target) > search * search) return "Outside search reach";
        return null;
    }
    private boolean targetableAfterLoss(Entity target) { return preyEligibility(target, true) == null; }
    private String preyEligibility(Entity target, boolean acquiring) {
        if(target == null) return "No target";
        if(target == this || target instanceof IGeminiCreature || target instanceof IBossDisplayData) return "Excluded creature";
        if(!target.isEntityAlive() || target.worldObj != worldObj || !target.addedToChunk) return "Dead or unloaded prey";
        if(!suitablePrey(target)) return "Unsuitable size or mount";
        if(target.getEntityData().hasKey("MockeryCaptor")) return "Held by Mockery";
        if(target.getEntityData().getLong("WoodcrawlerCaptorUntil") > worldObj.getTotalWorldTime()) return "Held by Woodcrawler";
        if(target.getEntityData().getLong("WoodcrawlerCocoonUntil") > worldObj.getTotalWorldTime()) return "Woodcrawler cocoon cooldown";
        if(acquiring && !GeminiCaptures.available(target)) return "Already captured or noclip";
        if(heldByOtherGardener(target)) return "Held by another Gardener";
        if(target.getEntityData().getLong("GardenerEscapeUntil") > worldObj.getTotalWorldTime()) return "Gardener escape cooldown";
        if(target instanceof EntityPlayer && (((EntityPlayer) target).capabilities.disableDamage
                || ((EntityPlayer) target).capabilities.isCreativeMode)) return "Invulnerable player";
        if(target.riddenByEntity instanceof EntityPlayer && (((EntityPlayer) target.riddenByEntity).capabilities.disableDamage
                || ((EntityPlayer) target.riddenByEntity).capabilities.isCreativeMode)) return "Invulnerable rider";
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
        if(surfaceHunter() && prey(preyTarget)) return;
        Entity closest = null;
        double best = Double.MAX_VALUE;
        int examined = 0;
        double scan = surfaceHunter() ? 48D : attackReach() + 7D;
        for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.expand(scan, isOcean() ? 32 : surfaceHunter() ? 16 : 8, scan))) {
            Entity target = (Entity) entry;
            if(!prey(target)) { captureRejection(target, preyRejection(target)); continue; }
            if(isOcean() && ++examined > 32) break;
            if(surfaceHunter() && (!surfaceHunting.available(target) || !canSeeSurfacePrey(target))) continue;
            String rejection = surfaceHunter() ? null : reachRejection(target, attackReach() + (target instanceof EntityPlayer ? 7D : 0D));
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
                if(!tipInReach(target)) {
                    score += 20000D;
                    if(surfaceHunter()) score += 64D * Math.abs(target.boundingBox.minY - boundingBox.minY);
                }
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
    private void captureRejection(Entity target, String reason) {
        if(isOcean() || !CAPTURE_DIAGNOSTICS || reason == null || target == null || !suitablePrey(target)
                || preyDistanceSq(target) > attackReach() * attackReach()
                || worldObj.getTotalWorldTime() < nextCaptureDiagnostic) return;
        nextCaptureDiagnostic = worldObj.getTotalWorldTime() + 100L;
        double dx = target.posX - posX, dz = target.posZ - posZ;
        MainRegistry.logger.info("[Gardener capture rejected] gardener=" + getEntityId() + " target=" + target.getEntityId()
                + " horizontal=" + Math.sqrt(dx * dx + dz * dz) + " vertical=" + (target.boundingBox.minY - boundingBox.minY)
                + " boxSeparation=" + Math.sqrt(preyDistanceSq(target)) + " eligible=" + prey(target)
                + " state=" + state() + " cooldown=" + cooldown + " captures=" + (CAPTURE_SLOTS - availableCaptures())
                + " escapeUntil=" + target.getEntityData().getLong("GardenerEscapeUntil") + ": " + reason);
    }

    @Override public void onUpdate() {
        double clientX = posX, clientY = posY, clientZ = posZ;
        motionX = motionY = motionZ = 0;
        super.onUpdate();
        previousCrawl = crawl;
        crawl += (dataWatcher.getWatchableObjectFloat(29) - crawl) * 0.25F;
        if(worldObj.isRemote) {
            if(!isOcean()) {
                locomotion.read(dataWatcher.getWatchableObjectInt(19));
                locomotion.advance(posX - clientX, posY - clientY, posZ - clientZ);
            }
            return;
        }
        traversalPose = false;
        if(addedToChunk) GardenerNests.remember(this);
        if(!isOcean() && (ticksExisted == 1 || ticksExisted % 100 == 0)) GardenerNests.bindResident(this);
        tickSurfaceBiomass();
        if(!anchored) anchor(posX, posY, posZ);
        if(!isOcean() && !GeminiSpawning.isManual(this) && nestY >= 8
                && (nestY <= 44 || GeminiSpawning.structureDebugAdmission(this) && nestY + 13 < worldObj.getActualHeight())) burrowing.enable();
        if(!active()) {
            release(GardenerNests.loaded(worldObj, activityBounds())
                    ? "Gardener deactivation" : "Chunk unloading");
            preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); dataWatcher.updateObject(28, (byte) 0);
            if(state() != IDLE) begin(IDLE);
            if(!isOcean()) surfaceHunting.stop("deactivation or unloaded bounds");
            if(!isOcean()) updateLocomotion(posX, posY, posZ, GardenerLocomotion.WALK);
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
        double commuteX = posX, commuteY = posY, commuteZ = posZ;
        boolean commuting = !isOcean() && burrowing.tick();
        boolean hunting = isOcean() || burrowing.hunting() && !hasHuntingMeal();
        if(!hunting) {
            preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1);
            if(state() != IDLE) begin(IDLE);
            smashCursor = -1;
        }
        if(ticksExisted % 20 == 1) maintainParts();
        int searchInterval = surfaceHunter() ? 16 : 20;
        int searchTick = surfaceHunter() ? ticksExisted + getEntityId() : ticksExisted;
        if(searchTick % searchInterval == 1 && hunting && freeCapture() != null && state() == IDLE) findTarget();
        Entity target = preyTarget;
        if(!prey(target) || freeCapture() == null) {
            captureRejection(target, preyRejection(target));
            if(surfaceHunter() && target != null && (!targetableAfterLoss(target) || freeCapture() == null))
                surfaceHunting.discard(target);
            preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); target = null;
        }
        if(surfaceHunter() && hunting && !surfaceHunting.remember(target)) {
            preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1); target = null;
        }
        if(!commuting) {
            if(surfaceHunter()) {
                if(hunting && state() == IDLE) surfaceHunting.tick(target);
                else if(crossingSurfaceEdge()) surfaceHunting.finishEdge();
            } else tickLocomotion(target);
        }
        if(!isOcean()) {
            if(locomotion.fullClimbPose() || burrowing.surface()) dataWatcher.updateObject(30, 0xFFFFFF);
            else if(ticksExisted % 10 == 1) adaptLimbs();
            updateLocomotion(commuteX, commuteY, commuteZ, hasHuntingMeal() ? GardenerLocomotion.CARRY
                    : surfaceHunter() && target != null ? GardenerLocomotion.PURSUE : GardenerLocomotion.WALK);
        }
        for(CaptureSlot slot : captures) slot.tick();
        if(cooldown > 0) cooldown--;
        if(state() == IDLE && !crossingSurfaceEdge() && freeCapture() != null && target != null && cooldown == 0
                && reach(target, attackReach()) && (isOcean() || tipInReach(target))) {
            int arm = captureArm(target);
            if(arm >= 0) {
                dataWatcher.updateObject(23, (byte) arm);
                boolean slam = !isOcean() && !surfaceHunter() && !beneathBody(target) && rand.nextInt(getHealth() < getMaxHealth() * 0.4F ? 2 : 3) == 0;
                aim(target, slam); begin(slam ? SLAM : REACH);
                playSound("mob.ghast.charge", 0.65F, 0.5F);
            } else {
                captureRejection(target, "No enabled tentacle with a clear reaching path");
                if(surfaceHunter()) surfaceHunting.blockedGrab(target);
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
        if(armDisabled(attackArm())) { begin(RECOVER); return; }
        Entity target = preyTarget;
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
                CaptureSlot slot = freeCapture();
                if(slot == null || captureFor(victim) != null) return;
                if(!slot.capture(victim, attackArm())) continue;
                preyTarget = null; setAttackTarget(null); dataWatcher.updateObject(20, -1);
                // The captured arm now belongs to its slot; another arm can start reaching.
                begin(IDLE); cooldown = 0;
                return;
            }
            if(attackTicks == 34 && surfaceHunter() && target != null) surfaceHunting.blockedGrab(target);
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
        for(int i = 0; i < 6; i++) if(!armDisabled(i) && captureForArm(i) == null) {
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
        if(isOcean() || !GeminiSpawning.isManual(this) && !hasSharedNest()) return;
        int extensions = dataWatcher.getWatchableObjectInt(30);
        for(int arm = 0; arm < GardenerPose.ARMS; arm++) {
            if(armState(arm) != IDLE || armDisabled(arm)) continue;
            int extension = 15;
            while(extension > 0 && !clearLimb(arm, extension / 15D)) extension--;
            // Leave room for idle sway between probes instead of resting the tip against a wall.
            if(extension < 15 && extension > 0) extension--;
            extensions = (extensions & ~(15 << (arm * 4))) | (extension << (arm * 4));
        }
        dataWatcher.updateObject(30, extensions);
    }
    /** Each slot owns its captive, transport, AI suspension and feeding/cocoon lifecycle. */
    private final class CaptureSlot {
        final int index;
        Entity captive;
        int captiveId = -1, arm = -1, state = IDLE, stateStart, attackTicks;
        UUID captiveUUID;
        EntityGardenerCocoon pendingCocoon;
        ChunkCoordinates selectedAttachment = new ChunkCoordinates(0, -1, 0);
        private static final int FEED_LIFT_TICKS = 14;
        int captureSneakTicks, captureTicks, movementObstructionTicks, lastFeedingProgress;
        int feedingTicks, liftTicks;
        Vec3 safeCaptivePosition;
        Vec3 releaseAim = Vec3.createVectorHelper(0, 0, 0);
        boolean attached, feeding, returningMeal;
        net.minecraftforge.event.entity.living.LivingDeathEvent feedingDeath;
        CaptureSlot(int index) { this.index = index; }
        Entity victim() {
            Entity entity = worldObj.isRemote ? worldObj.getEntityByID(captiveId) : captive;
            // 1.7.10 mob spawn packets carry runtime IDs, not the server's entity UUID.
            return entity != null && entity.worldObj == worldObj && entity.isEntityAlive()
                    && (worldObj.isRemote || entity.getUniqueID().equals(captiveUUID)) ? entity : null;
        }
        void begin(int next) {
            int previous = state;
            state = next; stateStart = (int) worldObj.getTotalWorldTime(); attackTicks = 0;
            syncCaptures();
            if(previous != next && captive != null)
                captureDiagnostic("Transition " + previous + " -> " + next);
        }
        void tick() {
            if(state == IDLE) return;
            attackTicks++;
            if(state == RELEASE) { if(attackTicks >= 24) { arm = -1; begin(IDLE); } return; }
            if(armDisabled(arm)) { release("Disabled tentacle"); return; }
            tickCapture();
        }
        boolean capture(Entity victim, int arm) {
            if(!GeminiCaptures.attach(EntityGardener.this, victim, false, this::release)) return false;
            returningMeal = surfaceHunter();
            this.arm = arm; captive = victim; captiveId = victim.getEntityId(); captiveUUID = victim.getUniqueID();
            victim.getEntityData().setString("GardenerCaptor", getUniqueID().toString());
            victim.getEntityData().setInteger("GardenerCaptorId", getEntityId());
            state = isOcean() ? HOLD : CAPTURE;
            EntityGardenerCocoon candidate = surfaceHunter() ? null : EntityGardenerCocoon.findCeiling(EntityGardener.this, victim);
            if(captive != victim) { if(candidate != null) candidate.cancelWrapping(); return true; }
            pendingCocoon = candidate;
            if(pendingCocoon != null) selectedAttachment = new ChunkCoordinates(
                    MathHelper.floor_double(pendingCocoon.posX), MathHelper.floor_double(pendingCocoon.boundingBox.maxY),
                    MathHelper.floor_double(pendingCocoon.posZ));
            captureTicks = captureSneakTicks = movementObstructionTicks = feedingTicks = lastFeedingProgress = 0;
            liftTicks = -1;
            attached = false;
            rememberClearGrip(captiveCenter());
            begin(state);
            captureDiagnostic("Initial attachment");
            if(surfaceHunter()) { surfaceHunting.stop("prey captured; carrying to nest"); burrowing.carryHome(); huntingDiagnostic("capture", "Captured prey " + victim.getEntityId() + "; carrying to nest"); }
            if(victim instanceof EntityLivingBase) GeminiInfections.expose((EntityLivingBase) victim, "gardener_capture:" + getUniqueID(), "gardener");
            else wound(victim, 10F);
            return true;
        }
        String snapshot() {
            return captiveId + "," + (captiveUUID == null ? "" : captiveUUID) + "," + state + "," + stateStart + "," + arm
                    + "," + selectedAttachment.posX + "," + selectedAttachment.posY + "," + selectedAttachment.posZ
                    + "," + releaseAim.xCoord + "," + releaseAim.yCoord + "," + releaseAim.zCoord;
        }
        void readSnapshot(String entry) {
            captive = null; captiveId = -1; captiveUUID = null; state = IDLE; arm = -1;
            if(entry.isEmpty()) return;
            String[] fields = entry.split(",", -1);
            if(fields.length != 11) return;
            captiveId = Integer.parseInt(fields[0]);
            captiveUUID = fields[1].isEmpty() ? null : UUID.fromString(fields[1]);
            state = Integer.parseInt(fields[2]); stateStart = Integer.parseInt(fields[3]); arm = Integer.parseInt(fields[4]);
            selectedAttachment = new ChunkCoordinates(Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), Integer.parseInt(fields[7]));
            releaseAim = Vec3.createVectorHelper(Double.parseDouble(fields[8]), Double.parseDouble(fields[9]), Double.parseDouble(fields[10]));
        }
        private void captureDiagnostic(String reason) {
            if(captive == null || !CAPTURE_DIAGNOSTICS && !huntingDebugEnabled()) return;
            Vec3 goal = pendingCocoon == null ? gripGoal(true) : pendingCocoon.victimGoal(captive);
            GeminiCaptures.diagnostic(EntityGardener.this, captive, Integer.toString(state), reason, feedingTicks, goal);
            if(CAPTURE_DIAGNOSTICS) MainRegistry.logger.info("[Gardener capture] captor=" + getEntityId()
                    + " victim=" + captive.getEntityId() + " slot=" + index + " state=" + state + " lift=" + liftTicks
                    + " feeding=" + feedingTicks + " distance=" + captiveCenter().distanceTo(goal) + " target=" + goal + " " + reason);
        }
        private void release(String reason) {
            captureDiagnostic(reason);
            release(true);
        }
        private void release(boolean escaped) {
            if(captive != null) restoreCaptivePosition();
            if(captive != null) releaseAim = Vec3.createVectorHelper(captive.posX - posX,
                    captive.boundingBox.minY + (isOcean() ? captive.height * 0.5D : gripHeight(captive)) - posY, captive.posZ - posZ);
            if(captive != null) {
                if(getUniqueID().toString().equals(captive.getEntityData().getString("GardenerCaptor"))) {
                    captive.getEntityData().removeTag("GardenerCaptor"); captive.getEntityData().removeTag("GardenerCaptorId");
                    captive.getEntityData().removeTag("GardenerDragSafe");
                }
                if(escaped) captive.getEntityData().setLong("GardenerEscapeUntil", worldObj.getTotalWorldTime() + 400L);
                GeminiCaptures.release(EntityGardener.this, captive);
            }
            captive = null;
            if(pendingCocoon != null) pendingCocoon.cancelWrapping();
            pendingCocoon = null;
            selectedAttachment = new ChunkCoordinates(0, -1, 0);
            safeCaptivePosition = null; attached = false;
            captiveId = -1; captiveUUID = null;
            liftTicks = -1; feeding = false; feedingDeath = null;
            returningMeal = false;
            if(state != IDLE && state != RELEASE) begin(RELEASE);
        }
        private Vec3 gripGoal(boolean feeding) {
            double angle = Math.toRadians(rotationYaw) + index * Math.PI * 2D / CAPTURE_SLOTS;
            double widest = captive.width;
            if(isOcean()) for(CaptureSlot slot : captures) if(slot.captive != null) widest = Math.max(widest, slot.captive.width);
            double radius, height;
            if(isOcean()) {
                radius = Math.max(2.8D, width * 0.5D + widest * 0.5D + 0.75D) + (feeding ? 0 : 0.7D);
                height = bodyOffset() + (feeding ? 2D : -0.4D);
            } else {
                radius = feeding ? 1.0D : captive.getBoundingBox() != null ? 0.9D : Math.max(1.6D, widest * 0.85D + 0.35D);
                height = captive.height * 0.5D + (feeding ? 1.55D + bodyShift(1) : 0.2D);
            }
            return Vec3.createVectorHelper(posX + Math.cos(angle) * radius, boundingBox.minY + height,
                    posZ + Math.sin(angle) * radius);
        }

        private void tickCapture() {
            captureTicks++;
            if(captive == null || !GeminiCaptures.valid(EntityGardener.this, captive) || !suitablePrey(captive)
                    || !getUniqueID().toString().equals(captive.getEntityData().getString("GardenerCaptor"))
                    || captive.getEntityData().getLong("GardenerEscapeUntil") > worldObj.getTotalWorldTime()
                    || captive instanceof EntityPlayer && (((EntityPlayer) captive).capabilities.disableDamage
                        || ((EntityPlayer) captive).capabilities.isCreativeMode)) {
                release("Invalid or escaped captive"); return;
            }
            captureSneakTicks = captive instanceof EntityPlayer && captive.isSneaking() ? captureSneakTicks + 1 : 0;
            if(captureSneakTicks >= 20) { release("Victim escaped"); return; }
            if(state == CAPTURE) {
                if(attackTicks < 16) return;
                begin(pendingCocoon == null ? HOLD : LIFT);
            }
            if(pendingCocoon != null && tickCocoon()) return;

            Vec3 hold = gripGoal(false);
            if(!attached) {
                if(!approachGrip(hold, isOcean() ? 0.42D : 0.48D, false)) {
                    if(captive != null && captureTicks > 180) release("Could not reach the carry grip within 180 ticks");
                    return;
                }
                attached = true;
                captureDiagnostic("Reached carry grip");
            }
            if(!isOcean() && burrowing.delivering()) {
                followGrip(hold);
                return;
            }
            if(liftTicks < 0) {
                liftTicks = 0;
                begin(LIFT);
                captureDiagnostic("Lifting to feeding position");
            }
            Vec3 goal = gripGoal(true);
            if(liftTicks < FEED_LIFT_TICKS) {
                double lift = GeminiCaptures.liftProgress(++liftTicks, FEED_LIFT_TICKS);
                if(!followGrip(hold.addVector((goal.xCoord - hold.xCoord) * lift,
                        (goal.yCoord - hold.yCoord) * lift, (goal.zCoord - hold.zCoord) * lift))) return;
                if(liftTicks == FEED_LIFT_TICKS) {
                    lastFeedingProgress = captureTicks;
                    begin(HOLD);
                    captureDiagnostic("Feeding started");
                }
                return;
            }
            if(!followGrip(goal)) return;
            // The bite clock belongs to the grip. Terrain, swimming and visual tip distance cannot restart it.
            if(++feedingTicks % 20 == 0) bite();
            if(captive != null && (captureTicks - lastFeedingProgress >= 120 || feedingTicks >= 2400 || isOcean() && captureTicks >= 1200))
                release("Feeding timed out without accepted damage or exceeded the hold limit");
        }

        private void bite() {
            Entity victim = captive;
            float health = victim instanceof EntityLivingBase ? ((EntityLivingBase) victim).getHealth() : 0;
            boolean fed, consumed;
            feeding = true; feedingDeath = null;
            try {
                fed = wound(victim, isOcean() ? 8F : 4F);
                consumed = feedingDeath != null && !feedingDeath.isCanceled();
            } finally { feeding = false; feedingDeath = null; }
            if(captive != victim) return;
            if(fed) {
                if(!(victim instanceof EntityLivingBase) || ((EntityLivingBase) victim).getHealth() < health)
                    lastFeedingProgress = captureTicks;
                playSound("mob.slime.attack", 0.8F, isOcean() ? 0.6F : 0.75F);
            }
            if(!victim.isEntityAlive()) {
                if(!isOcean()) {
                    if(fed && consumed) EntityGardenerCocoon.afterFeeding(EntityGardener.this, victim);
                    else EntityGardenerCocoon.failure(EntityGardener.this, "feeding death does not qualify: damage=" + fed
                            + ", confirmed uncanceled death=" + consumed);
                }
                if(captive == victim) {
                    captureDiagnostic("Feeding completed");
                    release(false);
                }
            }
        }

        /** Ceiling cocoons still require a clear grip and placement approval. */
        private boolean tickCocoon() {
            if(pendingCocoon.isDead || !pendingCocoon.attachmentValid()) {
                abandonCeiling("Invalid ceiling attachment"); return false;
            }
            Vec3 goal = pendingCocoon.victimGoal(captive);
            if(!approachGrip(goal, 0.24D, true)) {
                if(captive != null && (captureTicks >= 160 && state == LIFT || movementObstructionTicks >= 40))
                    abandonCeiling("Ceiling approach blocked; feeding locally");
                return true;
            }
            if(state == LIFT) {
                boolean placed = pendingCocoon.attach(EntityGardener.this, captive);
                if(captive == null) return true;
                if(placed) { movementObstructionTicks = 0; begin(ATTACH); }
                else if(!pendingCocoon.placementObstructed() || ++movementObstructionTicks >= 40)
                    abandonCeiling("Cocoon placement rejected; feeding locally");
            } else if(state == ATTACH && attackTicks >= 15) begin(WRAP);
            else if(state == WRAP) {
                if(!pendingCocoon.fullyWrapped()) pendingCocoon.growWrapping();
                if(pendingCocoon.fullyWrapped()) {
                    boolean sealed = pendingCocoon.seal(EntityGardener.this, captive);
                    if(captive == null) return true;
                    if(sealed) { captureDiagnostic("Wrapping completed"); release(false); }
                    else if(!pendingCocoon.placementObstructed() || ++movementObstructionTicks >= 40)
                        abandonCeiling("Cocoon sealing rejected; feeding locally");
                }
            }
            return true;
        }

        private void abandonCeiling(String reason) {
            captureDiagnostic(reason);
            pendingCocoon.cancelWrapping();
            pendingCocoon = null;
            selectedAttachment = new ChunkCoordinates(0, -1, 0);
            begin(HOLD);
        }

        private boolean approachGrip(Vec3 goal, double speed, boolean ceiling) {
            Vec3 here = captiveCenter();
            double distance = here.distanceTo(goal);
            double step = distance <= speed ? 1D : speed / distance;
            Vec3 next = here.addVector((goal.xCoord - here.xCoord) * step,
                    (goal.yCoord - here.yCoord) * step, (goal.zCoord - here.zCoord) * step);
            if(ceiling && !clearCaptiveSegment(here, next)) {
                movementObstructionTicks++;
                return false;
            }
            if(ceiling && step < 1D) movementObstructionTicks = 0;
            return followGrip(next) && step == 1D;
        }

        private boolean followGrip(Vec3 point) {
            if(!loadedCaptiveSegment(point, point)) { release("Attachment left loaded terrain"); return false; }
            positionCaptive(point);
            if(captureTicks % 5 == 0) rememberClearGrip(point);
            return true;
        }

        private void rememberClearGrip(Vec3 point) {
            if(!clearCaptiveSegment(point, point)) return;
            safeCaptivePosition = point;
            NBTTagCompound safe = captive.getEntityData().getCompoundTag("GardenerDragSafe");
            safe.setDouble("X", point.xCoord); safe.setDouble("Y", point.yCoord); safe.setDouble("Z", point.zCoord);
            captive.getEntityData().setTag("GardenerDragSafe", safe);
        }
        private Vec3 captiveCenter() {
            return Vec3.createVectorHelper(captive.posX, captive.boundingBox.minY + captive.height * 0.5D, captive.posZ);
        }
        private void positionCaptive(Vec3 center) {
            GeminiCaptures.center(captive, center.xCoord, center.yCoord, center.zCoord);
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
            if(safe != null) captive.setPosition(safe.xCoord, safe.yCoord - captive.height * 0.5D
                    + captive.posY - captive.boundingBox.minY, safe.zCoord);
        }
    }

    /** Gardener captures are transient; an old save may leave a victim inside a wall. */
    public static void clearOrphanedGrip(Entity victim) {
        if(victim.worldObj.isRemote || !victim.getEntityData().hasKey("GardenerCaptor")) return;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("GardenerCaptorId"));
        if(owner instanceof EntityGardener && GeminiCaptures.heldBy(owner, victim)) return;
        restoreSavedCaptive(victim);
        victim.getEntityData().removeTag("GardenerCaptor");
        victim.getEntityData().removeTag("GardenerCaptorId");
    }
    private static void restoreSavedCaptive(Entity victim) {
        if(!victim.getEntityData().hasKey("GardenerDragSafe")) return;
        NBTTagCompound safe = victim.getEntityData().getCompoundTag("GardenerDragSafe");
        double x = safe.getDouble("X"), y = safe.getDouble("Y"), z = safe.getDouble("Z");
        AxisAlignedBB box = victim.boundingBox.getOffsetBoundingBox(x - victim.posX,
                y - victim.boundingBox.minY - victim.height * 0.5D, z - victim.posZ);
        if(GardenerNests.loaded(victim.worldObj, box.expand(1, 0, 1)) && victim.worldObj.func_147461_a(box).isEmpty())
            victim.setPosition(x, y - victim.height * 0.5D + victim.posY - victim.boundingBox.minY, z);
        victim.getEntityData().removeTag("GardenerDragSafe");
    }
    /** The drag can pass through terrain while the limb supports the prey. */
    public static boolean preventsDraggingSuffocation(EntityLivingBase victim) {
        if(victim.worldObj.isRemote || !victim.getEntityData().hasKey("GardenerCaptor")) return false;
        Entity owner = victim.worldObj.getEntityByID(victim.getEntityData().getInteger("GardenerCaptorId"));
        return owner instanceof EntityGardener && GeminiCaptures.heldBy(owner, victim);
    }
    public void recordFeedingDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        CaptureSlot slot = captureFor(event.entityLiving);
        if(slot != null && slot.feeding && event.source.getEntity() == this) slot.feedingDeath = event;
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
    public boolean isHolding(Entity entity) {
        return captureFor(entity) != null && entity.worldObj == worldObj && isEntityAlive() && GeminiCaptures.heldBy(this, entity);
    }
    boolean isPreparingCocoon(EntityGardenerCocoon cocoon, Entity victim) {
        CaptureSlot slot = captureFor(victim);
        return slot != null && slot.pendingCocoon == cocoon && isHolding(victim) && (slot.state == ATTACH || slot.state == WRAP);
    }
    public void releaseForChunkUnload(int x, int z) {
        if(chunkCoordX == x && chunkCoordZ == z) release("Chunk unloading");
        else for(CaptureSlot slot : captures)
            if(slot.captive != null && slot.captive.chunkCoordX == x && slot.captive.chunkCoordZ == z) slot.release("Chunk unloading");
    }
    public void releaseForWorldUnload() { release("Chunk unloading: world unload"); }
    private int availableCaptures() {
        int free = 0;
        for(CaptureSlot slot : captures) if(slot.captive == null) free++;
        return free;
    }
    /** Short collision-tested steering probes instead of an enormous vanilla cave path search. */
    private void tickLocomotion(Entity target) {
        if(captureState() || state() != IDLE || !isOcean() && target != null && tipInReach(target) && reach(target, attackReach())) {
            if(!isOcean()) travelX = travelY = travelZ = 0;
            if(isOcean()) dataWatcher.updateObject(29, 0F);
            return;
        }
        if(ticksExisted % 10 == 1) {
            travelX = travelY = travelZ = 0;
            double gx = isOcean() ? nestX : burrowing.patrolX(), gy = nestY, gz = isOcean() ? nestZ : burrowing.patrolZ();
            if(hasSharedNest() && !burrowing.surface()) {
                if(ticksExisted % 600 < 10 || patrolX == 0 && patrolZ == 0 || getDistanceSq(patrolX, nestY, patrolZ) < 4D) {
                    double angle = (ticksExisted / 600 + getUniqueID().hashCode()) * 2.399963D;
                    double px = sharedX + Math.cos(angle) * 26, pz = sharedZ + Math.sin(angle) * 26;
                    if(GardenerNests.inHabitat(px - sharedX, pz - sharedZ, 18)) { patrolX = px; patrolZ = pz; }
                    else { patrolX = nestX; patrolZ = nestZ; }
                }
                gx = patrolX; gz = patrolZ;
            }
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
                for(int attempt = 0; attempt < (hasSharedNest() ? 9 : 5); attempt++) {
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
        if(isOcean()) dataWatcher.updateObject(29, moved > 0.005D ? 1F : 0F);
    }
    protected double travelVertical(double goalY, double speed) {
        return isOcean() ? MathHelper.clamp_double(goalY - posY, -speed * 0.5D, speed * 0.5D) : 0;
    }
    /** Bypass the public physics lock only for the authoritative territorial steering. */
    protected void moveGardener(double x, double y, double z) { super.moveEntity(x, y, z); }
    protected boolean canTravel(double x, double y, double z) {
        AxisAlignedBB next = boundingBox.getOffsetBoundingBox(x, y, z);
        if(hasSharedNest() && !burrowing.surface()) {
            if(!GardenerNests.inHabitat(posX + x - sharedX, posZ + z - sharedZ, 18)) return false;
            // Keep the entire moving model above growth and below the lowest ceiling.
            AxisAlignedBB model = AxisAlignedBB.getBoundingBox(posX + x - 15, next.minY + 0.05D, posZ + z - 15,
                    posX + x + 15, next.minY + 11, posZ + z + 15);
            if(!GardenerNests.loaded(worldObj, model) || !worldObj.func_147461_a(model).isEmpty() || worldObj.isAnyLiquid(model)) return false;
            AxisAlignedBB neighbors = next.expand(31, 8, 31);
            for(Object entry : worldObj.getEntitiesWithinAABB(EntityGardener.class, neighbors)) {
                EntityGardener other = (EntityGardener) entry;
                if(other == this || !other.isEntityAlive() || other.isOcean() || Math.abs(other.posY - posY) > 10) continue;
                double before = (posX - other.posX) * (posX - other.posX) + (posZ - other.posZ) * (posZ - other.posZ);
                double after = (posX + x - other.posX) * (posX + x - other.posX) + (posZ + z - other.posZ) * (posZ + z - other.posZ);
                if(after < 32D * 32D && after < before) return false; // Can always separate; no overlap deadlock.
            }
        }
        if(!GardenerNests.loaded(worldObj, next.expand(GeminiSpawning.isManual(this) ? 1 : 19, 2,
                GeminiSpawning.isManual(this) ? 1 : 19)) || !burrowing.surface() && Math.abs(next.minY - nestY) > 4D
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
            CaptureSlot capture = captureForArm(arm);
            if(capture != null) capture.release("Disabled tentacle");
            if(attackArm() == arm && state() != IDLE) begin(RECOVER);
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
    @Override public void onDeath(DamageSource source) {
        if(!worldObj.isRemote) { release("Gardener deactivation: death"); removeParts(); }
        super.onDeath(source);
        // Forge's cancelable death event runs inside super. Release the identity only after acceptance.
        if(!worldObj.isRemote && dead && getHealth() <= 0) GardenerNests.defeated(this);
    }
    @Override public void setDead() {
        if(!worldObj.isRemote) { release("Gardener deactivation: removed"); removeParts(); GardenerNests.defeated(this); }
        super.setDead();
    }
    @Override public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        GardenerNests.remember(this);
        if(!isOcean()) burrowing.write(nbt);
        if(!isOcean()) { nbt.setBoolean("SharedNest", sharedNest); nbt.setDouble("SharedNestX", sharedX); nbt.setDouble("SharedNestZ", sharedZ); }
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
        if(!isOcean()) burrowing.read(nbt);
        if(!isOcean()) { sharedNest = nbt.getBoolean("SharedNest"); sharedX = nbt.getDouble("SharedNestX"); sharedZ = nbt.getDouble("SharedNestZ"); }
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
        release("Capture reset on load"); begin(IDLE);
    }
}
