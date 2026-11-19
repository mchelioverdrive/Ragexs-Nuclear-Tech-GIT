package com.hbm.entity.mob.gemini;

import java.util.UUID;
import com.hbm.handler.GeminiCaptures;
import com.hbm.handler.WoodcrawlerCaptures;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInfections;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import com.hbm.world.gemini.FakePersonSpawning;

/** The live victim remains its own saved entity. UUIDs are links, never entity snapshots. */
public final class WoodcrawlerCapture {
    private static final int FEED_LIFT_TICKS = 14;
    private final EntityWoodcrawler mob;
    private EntityLivingBase victim;
    private UUID restore;
    private GardenerNests.Delivery destination;
    private int age, missing, search, blocked, struggle, previousInput, inputDelay;
    private float interrupts;
    private boolean feeding, biting;
    private int feedingAge, feedingCycle, feedingDelay, liftTicks;
    private DamageSource biteSource;
    private LivingDeathEvent biteDeath;
    private double liftX, liftY, liftZ, feedingAngle;

    WoodcrawlerCapture(EntityWoodcrawler mob) { this.mob = mob; }
    public boolean active() { return victim != null || restore != null; }
    public boolean owns(EntityLivingBase e) { return victim == e; }
    public EntityLivingBase victim() { return victim; }
    public boolean feeding() { return feeding; }
    public boolean take(EntityLivingBase prey) {
        if(active() || !mob.isPrey(prey) || !WoodcrawlerCaptures.available(prey)
                || prey.width > 1.1F || prey.height > 2.5F || prey.isEntityInvulnerable()) return false;
        if(!bind(prey)) return false;
        age = blocked = struggle = previousInput = inputDelay = search = 0; interrupts = 0;
        feeding = false; feedingAge = feedingCycle = feedingDelay = 0;
        mob.setCrouching(true);
        if(prey instanceof EntityPlayer) ((EntityPlayer) prey).addChatMessage(new ChatComponentTranslation("woodcrawler.capture.escape"));
        return true;
    }
    private boolean bind(EntityLivingBase prey) {
        if(!GeminiCaptures.attach(mob, prey, true, this::release)) return false;
        victim = prey;
        tag();
        mob.captureVisual(prey);
        WoodcrawlerCaptures.acceptInput(prey, true);
        GeminiCaptures.diagnostic(mob, prey, "restraining", "Initial attachment", age, mob.getProboscisOrigin(1));
        return true;
    }
    private void tag() {
        NBTTagCompound n = victim.getEntityData();
        n.setString("WoodcrawlerCaptor", mob.getUniqueID().toString());
        n.setInteger("WoodcrawlerCaptorId", mob.getEntityId());
        n.setLong("WoodcrawlerCaptorUntil", mob.worldObj.getTotalWorldTime() + 140);
    }
    private boolean valid() {
        return victim != null && GeminiCaptures.valid(mob, victim)
                && !(victim instanceof EntityPlayer && ((EntityPlayer) victim).capabilities.disableDamage);
    }
    public void tick() {
        if(restore != null) {
            if(++missing % 10 == 1) for(Object entry : mob.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, mob.boundingBox.expand(8, 5, 8))) {
                EntityLivingBase candidate = (EntityLivingBase) entry;
                if(restore.equals(candidate.getUniqueID()) && mob.getUniqueID().toString().equals(candidate.getEntityData().getString("WoodcrawlerCaptor"))
                        && candidate.isEntityAlive() && bind(candidate)) {
                    restore = null;
                    break;
                }
            }
            if(restore != null) { if(missing >= 100) release(); return; }
        }
        if(!valid() || !feeding && mob.getDistanceSqToEntity(victim) > 36) { release("Invalid victim or drag beyond proboscis range"); return; }
        tag(); ++age;
        if(inputDelay > 0) inputDelay--;
        if(victim instanceof EntityPlayer) {
            int input = WoodcrawlerCaptures.input(victim);
            if(input != 0 && input != previousInput && inputDelay == 0) {
                struggle = Math.min(100, struggle + 3); previousInput = input; inputDelay = 5;
            } else if(input == 0) previousInput = 0;
            if(age % 20 == 0) struggle = Math.max(0, struggle - 1);
            if(struggle >= 100) { release("Victim escaped"); return; }
        }
        if(feeding && victim instanceof EntityPlayer) return;
        feeding = false;
        if(--search <= 0) {
            search = 80;
            GardenerNests.Delivery previous = destination;
            if(destination != null && !GardenerNests.claimDelivery(mob, victim, destination)) destination = null;
            if(destination == null) for(GardenerNests.Delivery option : GardenerNests.deliveries(mob, victim)) {
                Vec3 goal = option.waypoint(mob);
                if((option.atAccess(mob) || mob.canTransportTo(goal)) && GardenerNests.claimDelivery(mob, victim, option)) { destination = option; break; }
            }
            if(previous != destination)
                GeminiCaptures.diagnostic(mob, victim, destination == null ? "restraining" : "transporting",
                        destination == null ? "Nest destination lost" : "Taking prey to nest", age,
                        destination == null ? null : destination.waypoint(mob));
        }
        if(age > 2400 || blocked > 80) { localDelivery(); return; }
        if(destination == null && age >= (victim instanceof EntityPlayer ? 40 : 100)) { localDelivery(); return; }
        if(destination != null && destination.inChamber(mob) && victim.getDistanceSq(destination.x, destination.y, destination.z) < 4) {
            localDelivery(); return;
        }
        mob.capturePhase(destination == null ? EntityWoodcrawler.CaptureState.RESTRAINING : EntityWoodcrawler.CaptureState.TRANSPORTING);
    }
    public Vec3 goal() { return feeding || destination == null ? null : destination.waypoint(mob); }
    public boolean damage(Entity source, float amount) {
        if(!active()) return false;
        // A captive cannot repeatedly interrupt its own secure hold; rescuers and other damage can.
        if(source != victim) {
            if(feeding) { feedingDelay = 40; feedingCycle = 0; mob.stopFeedingVisual(); }
            interrupts += amount;
        }
        if(interrupts >= 10) { release("Rescue damage broke the grip"); return true; }
        return false;
    }
    /** Do not let a climbing body leave its larger victim behind in a narrow opening. */
    public boolean canMove(double x, double y, double z) {
        if(victim == null || x * x + y * y + z * z < 0.000001) return true;
        if(y < 0) {
            AxisAlignedBB down = victim.boundingBox.addCoord(0, y, 0).contract(0.001, 0.001, 0.001);
            if(!GardenerNests.loaded(mob.worldObj, down)) return false;
            // Native moveEntity clips gravity against support. A held entity's onGround flag
            // can be false after a horizontal pull, so derive this from collision geometry.
            if(!mob.worldObj.func_147461_a(down).isEmpty()) y = 0;
        }
        AxisAlignedBB sweep = victim.boundingBox.addCoord(x, y, z).contract(0.001, 0.001, 0.001);
        if(!GardenerNests.loaded(mob.worldObj, sweep) || mob.worldObj.isAnyLiquid(sweep)) return false;
        if(mob.worldObj.func_147461_a(sweep).isEmpty()) return true;
        // A native one-block body step may require lifting the victim before pulling horizontally.
        AxisAlignedBB lift = victim.boundingBox.addCoord(0, 1, 0).contract(0.001, 0.001, 0.001);
        AxisAlignedBB raised = victim.boundingBox.getOffsetBoundingBox(0, 1, 0).addCoord(x, Math.max(0, y), z);
        AxisAlignedBB support = victim.boundingBox.getOffsetBoundingBox(0, -0.06D, 0).contract(0.001, 0.001, 0.001);
        return Math.abs(y) < 0.5 && GardenerNests.loaded(mob.worldObj, raised)
                && !mob.worldObj.func_147461_a(support).isEmpty()
                && mob.worldObj.func_147461_a(lift).isEmpty() && mob.worldObj.func_147461_a(raised).isEmpty();
    }
    /** Called after the carrier's movement and pose have settled for this tick. */
    void position() {
        if(victim == null) return;
        if(!valid()) { release("Invalid victim"); return; }
        if(feeding) { feed(); return; }
        double dx = mob.posX - victim.posX, dz = mob.posZ - victim.posZ;
        double dy = mob.boundingBox.minY - victim.boundingBox.minY;
        double horizontal = Math.hypot(dx, dz), distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double scale = Math.min(0.4, Math.max(0, distance - (Math.abs(dy) > 0.3 ? 0 : 1.1))) / Math.max(0.001, distance);
        dx *= scale; dy *= scale; dz *= scale;
        AxisAlignedBB sweep = victim.boundingBox.addCoord(dx, dy, dz).contract(0.001, 0.001, 0.001);
        if(!GardenerNests.loaded(mob.worldObj, sweep)) { release("Drag entered unloaded terrain"); return; }
        if(!mob.worldObj.func_147461_a(sweep).isEmpty() && mob.boundingBox.minY > victim.boundingBox.minY + 0.2) {
            dx = dz = 0; dy = Math.min(0.4, mob.boundingBox.minY - victim.boundingBox.minY);
            sweep = victim.boundingBox.addCoord(0, dy, 0).contract(0.001, 0.001, 0.001);
        }
        if(!mob.worldObj.func_147461_a(sweep).isEmpty() && dy < 0) {
            // Clear the lip of a shaft horizontally before lowering the upright captive.
            dy = 0; sweep = victim.boundingBox.addCoord(dx, 0, dz).contract(0.001, 0.001, 0.001);
        }
        if(!GardenerNests.loaded(mob.worldObj, sweep) || !mob.worldObj.func_147461_a(sweep).isEmpty() || mob.worldObj.isAnyLiquid(sweep)) {
            if(++blocked > 80) release("Drag obstructed");
        } else {
            GeminiCaptures.position(victim, victim.posX + dx, victim.posY + dy, victim.posZ + dz);
            if(horizontal > 2.5 && dx * dx + dz * dz < 0.00001) blocked++;
            else blocked = Math.max(0, blocked - 1);
        }
        if(victim == null) return;
        if(!mob.worldObj.func_147461_a(victim.boundingBox.contract(0.001, 0.001, 0.001)).isEmpty()) release("Drag grip obstructed");
    }
    private void localDelivery() {
        if(victim instanceof EntityPlayer) {
            feeding = true; feedingAge = feedingCycle = 0;
            liftTicks = blocked = 0;
            mob.capturePhase(EntityWoodcrawler.CaptureState.FEEDING);
            return;
        } else if(victim instanceof EntityVillager) {
            NBTTagCompound snapshot = new NBTTagCompound();
            if(victim.writeToNBTOptional(snapshot)) {
                // The cocoon receives the real villager; never save a second copy of transport ownership.
                snapshot.getCompoundTag("ForgeData").removeTag("WoodcrawlerCaptor");
                snapshot.getCompoundTag("ForgeData").removeTag("WoodcrawlerCaptorId");
                snapshot.getCompoundTag("ForgeData").removeTag("WoodcrawlerCaptorUntil");
                EntityWoodcrawlerCocoon cocoon = EntityWoodcrawlerCocoon.place(mob, snapshot, victim.posX, victim.boundingBox.minY, victim.posZ, 0, (byte) 0);
                if(cocoon != null) {
                    EntityVillager delivered = (EntityVillager) victim;
                    release("Delivered to cocoon");
                    if(!cocoon.bindVictim(delivered)) cocoon.cancelPlacement();
                    return;
                }
            }
        } else if(!(victim instanceof EntityPlayer) && destination != null && destination.inChamber(mob)) {
            // Existing infection owns non-player, non-villager assimilation.
            GeminiInfections.exposeContact(victim, "woodcrawler-delivery:" + mob.getUniqueID(), "woodcrawler");
        }
        release();
    }
    private void feed() {
        mob.capturePhase(EntityWoodcrawler.CaptureState.FEEDING);
        // Invulnerability, healing and protection hooks must not produce an endless hold.
        if(++feedingAge > 600 || WoodcrawlerCaptures.owner(victim) != this) { release("Feeding hold timed out or lost ownership"); return; }
        if(!liftToProboscis()) return;
        if(feedingDelay > 0) { feedingDelay--; return; }
        int phase = ++feedingCycle % 40;
        if(!mob.feedingVisual(victim, phase)) {
            mob.stopFeedingVisual();
            if(++blocked > 40) release("Feeding proboscis obstructed");
            return;
        }
        blocked = 0;
        if(phase != 14) return; // Exactly one wound at full visible extension per cycle.
        EntityPlayer eaten = (EntityPlayer) victim;
        biting = true; biteDeath = null; biteSource = DamageSource.causeMobDamage(mob);
        try {
            boolean hit = eaten.attackEntityFrom(biteSource, 4F);
            // Evaluate the event only after all listeners and normal death/drop handling finish.
            if(hit && biteDeath != null && !biteDeath.isCanceled() && !eaten.isEntityAlive()) {
                GeminiCaptures.diagnostic(mob, eaten, "feeding", "Feeding completed", feedingAge, mob.getProboscisOrigin(1));
                FakePersonSpawning.convertPlayer(mob, eaten);
            }
        } finally {
            biting = false; biteDeath = null; biteSource = null;
            if(!eaten.isEntityAlive()) release();
        }
    }
    private boolean liftToProboscis() {
        Vec3 origin = mob.getProboscisOrigin(1);
        double yaw = Math.toRadians(mob.renderYawOffset);
        double dx = victim.posX - origin.xCoord, dz = victim.posZ - origin.zCoord;
        double centerY = victim.boundingBox.minY + victim.height * 0.5D;
        if(liftTicks == 0 && blocked == 0) {
            feedingAngle = Math.atan2(dz, dx) - yaw;
            liftX = dx * Math.cos(yaw) + dz * Math.sin(yaw);
            liftY = centerY - origin.yCoord;
            liftZ = dz * Math.cos(yaw) - dx * Math.sin(yaw);
            GeminiCaptures.diagnostic(mob, victim, "lift", "Pulling prey toward proboscis", feedingAge, origin);
        }
        double t = GeminiCaptures.liftProgress(liftTicks + 1, FEED_LIFT_TICKS);
        double x = origin.xCoord + (liftX * Math.cos(yaw) - liftZ * Math.sin(yaw)) * (1 - t)
                + Math.cos(feedingAngle + yaw) * 1.1D * t;
        double y = origin.yCoord + liftY * (1 - t) + Math.max(-0.28D,
                mob.boundingBox.minY + victim.height * 0.5D + 0.12D - origin.yCoord) * t;
        double z = origin.zCoord + (liftX * Math.sin(yaw) + liftZ * Math.cos(yaw)) * (1 - t)
                + Math.sin(feedingAngle + yaw) * 1.1D * t;
        AxisAlignedBB swept = victim.boundingBox.addCoord(x - victim.posX, y - centerY, z - victim.posZ).contract(0.001, 0.001, 0.001);
        if(!GardenerNests.loaded(mob.worldObj, swept) || !mob.worldObj.func_147461_a(swept).isEmpty()) {
            if(++blocked > 40) release("Feeding lift obstructed");
            return false;
        }
        GeminiCaptures.center(victim, x, y, z);
        if(liftTicks < FEED_LIFT_TICKS) {
            blocked = 0;
            if(++liftTicks == FEED_LIFT_TICKS)
                GeminiCaptures.diagnostic(mob, victim, "feeding", "Reached feeding position", feedingAge, Vec3.createVectorHelper(x, y, z));
            return false;
        }
        return true;
    }
    public void death(LivingDeathEvent event) {
        if(event.entityLiving != victim) return;
        if(biting && event.source == biteSource && event.source.getEntity() == mob) biteDeath = event;
        else release();
    }
    public void release() { release("Capture ended"); }
    private void release(String reason) {
        GeminiCaptures.diagnostic(mob, victim, feeding ? "feeding" : "transport", reason, feeding ? feedingAge : age, null);
        EntityLivingBase old = victim; victim = null; restore = null;
        feeding = false; feedingAge = feedingCycle = feedingDelay = liftTicks = 0; mob.stopFeedingVisual();
        if(destination != null) GardenerNests.releaseDelivery(mob, destination);
        destination = null;
        if(old != null) {
            NBTTagCompound n = old.getEntityData();
            if(mob.getUniqueID().toString().equals(n.getString("WoodcrawlerCaptor"))) {
                n.removeTag("WoodcrawlerCaptor"); n.removeTag("WoodcrawlerCaptorId"); n.removeTag("WoodcrawlerCaptorUntil");
            }
            n.setLong("WoodcrawlerEscapeUntil", old.worldObj.getTotalWorldTime() + 200);
            WoodcrawlerCaptures.acceptInput(old, false);
            // Exceptional terrain edits must not leave a released living victim inside a new solid block.
            if(old.worldObj == mob.worldObj && old.isEntityAlive() && GardenerNests.loaded(old.worldObj, old.boundingBox)
                    && !old.worldObj.func_147461_a(old.boundingBox.contract(0.001, 0.001, 0.001)).isEmpty()) {
                boolean recovered = false;
                for(int y = 0; y <= 2 && !recovered; y++) for(int i = 0; i < 9 && !recovered; i++) {
                    double dx = (i % 3 - 1) * 0.8, dz = (i / 3 - 1) * 0.8;
                    AxisAlignedBB safe = old.boundingBox.getOffsetBoundingBox(dx, y, dz);
                    if(GardenerNests.loaded(old.worldObj, safe) && old.worldObj.func_147461_a(safe).isEmpty() && !old.worldObj.isAnyLiquid(safe)) {
                        old.setPosition(old.posX + dx, old.posY + y, old.posZ + dz); recovered = true;
                    }
                }
            }
            GeminiCaptures.release(mob, old);
        }
        mob.captureVisual(null);
    }
    public void write(NBTTagCompound n) {
        UUID id = victim == null ? restore : victim.getUniqueID();
        if(id == null) return;
        n.setString("WoodcrawlerLiveVictim", id.toString()); n.setInteger("WoodcrawlerTransportAge", age);
        n.setInteger("WoodcrawlerStruggle", struggle);
        n.setBoolean("WoodcrawlerFeeding", feeding); n.setInteger("WoodcrawlerFeedingAge", feedingAge);
        if(destination != null) n.setTag("WoodcrawlerDelivery", destination.write());
    }
    public void read(NBTTagCompound n) {
        try { restore = UUID.fromString(n.getString("WoodcrawlerLiveVictim")); }
        catch(IllegalArgumentException ignored) { restore = null; }
        age = MathHelper.clamp_int(n.getInteger("WoodcrawlerTransportAge"), 0, 2400);
        struggle = MathHelper.clamp_int(n.getInteger("WoodcrawlerStruggle"), 0, 99);
        feeding = restore != null && n.getBoolean("WoodcrawlerFeeding");
        feedingAge = MathHelper.clamp_int(n.getInteger("WoodcrawlerFeedingAge"), 0, 600);
        if(n.hasKey("WoodcrawlerDelivery", 10)) destination = GardenerNests.Delivery.read(n.getCompoundTag("WoodcrawlerDelivery"));
    }
}
