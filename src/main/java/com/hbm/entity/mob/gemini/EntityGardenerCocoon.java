package com.hbm.entity.mob.gemini;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeminiConfig;
import com.hbm.main.MainRegistry;
import com.hbm.world.gemini.GardenerNests;
import com.hbm.world.gemini.GeminiInvasionManager;
import com.hbm.world.gemini.GeminiPopulation;
import com.hbm.world.gemini.GeminiSpawning;
import com.hbm.world.gemini.WoodcrawlerSpawning;
import com.hbm.world.gemini.WretchCamps;
import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** Gardener biomass, independent of the villager/Fake Person processing nest. No terrain mutations. */
public final class EntityGardenerCocoon extends Entity implements IGeminiCreature {
    private int remaining, duration, retry, collapse;
    private boolean sealed, completed, manual, resolving;
    private String placementFailure;

    public EntityGardenerCocoon(World world) {
        super(world); setSize(1.5F, 3F); preventEntitySpawning = true;
    }
    @Override protected void entityInit() {
        dataWatcher.addObject(20, (byte) 0); // Hanging / surface.
        dataWatcher.addObject(21, (byte) 0); // Coarse visual maturity; never an interface progress bar.
        dataWatcher.addObject(22, (byte) 0); // Empty, collapsing shell.
    }
    public boolean isHanging() { return dataWatcher.getWatchableObjectByte(20) == 0; }
    public float maturity() { return dataWatcher.getWatchableObjectByte(21) / 100F; }
    public boolean isEmpty() { return dataWatcher.getWatchableObjectByte(22) != 0; }
    public float collapseAmount() { return Math.max(0, dataWatcher.getWatchableObjectByte(22) - 1) / 30F; }
    private void form(boolean hanging) {
        dataWatcher.updateObject(20, (byte) (hanging ? 0 : 1));
        setSize(hanging ? 1.5F : 2F, hanging ? 3F : 1F);
        setPosition(posX, posY, posZ);
    }
    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public void moveEntity(double x, double y, double z) { }

    public static boolean suitable(Entity prey) {
        return prey instanceof EntityLiving && !(prey instanceof IGeminiCreature) && !(prey instanceof IBossDisplayData)
                && !prey.noClip && prey.width <= 1.1F && prey.height <= 2.5F
                && prey.ridingEntity == null && prey.riddenByEntity == null;
    }
    private static boolean enabled(EntityGardener parent) {
        return !parent.isOcean() && GeminiSpawning.canAct(parent, 3)
                && parent.worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                && GeminiPopulation.canAdmitCocoon(parent.worldObj);
    }
    static void failure(EntityGardener parent, String reason) {
        MainRegistry.logger.info("[Gardener cocoon] gardener=" + parent.getUniqueID() + " dimension="
                + parent.worldObj.provider.dimensionId + " at=" + parent.posX + "," + parent.posY + "," + parent.posZ + ": " + reason);
    }
    private static boolean admission(EntityGardener parent) {
        if(enabled(parent)) return true;
        failure(parent, GeminiPopulation.canAdmitCocoon(parent.worldObj)
                ? "activity/progression or doMobSpawning rejects cocoon" : "population limits reject cocoon");
        return false;
    }
    private static boolean naturalSupport(World world, int x, int y, int z, boolean hanging) {
        Block block = world.getBlock(x, y, z);
        boolean natural = block == Blocks.stone || block == Blocks.dirt || block == Blocks.grass || block == Blocks.gravel
                || block == Blocks.sandstone || block == ModBlocks.gardener_flesh;
        return natural && world.getTileEntity(x, y, z) == null
                && !block.hasTileEntity(world.getBlockMetadata(x, y, z))
                && block.isSideSolid(world, x, y, z, hanging ? ForgeDirection.DOWN : ForgeDirection.UP);
    }
    /** Fast loaded-only geometry check, also used during transport and after attachment. */
    public boolean attachmentValid() {
        int x = MathHelper.floor_double(posX), z = MathHelper.floor_double(posZ);
        int y = MathHelper.floor_double(isHanging() ? boundingBox.maxY + 0.01D : posY - 0.01D);
        return posY > 1 && boundingBox.maxY < worldObj.getHeight() - 1
                && GardenerNests.loaded(worldObj, boundingBox.expand(1, 1, 1))
                && naturalSupport(worldObj, x, y, z, isHanging())
                && (!isHanging() || !worldObj.canBlockSeeTheSky(x, y - 1, z))
                && worldObj.getCollidingBoundingBoxes(this, boundingBox).isEmpty()
                && !worldObj.isAnyLiquid(boundingBox);
    }
    private boolean site(EntityGardener parent, Entity victim) {
        placementFailure = "invalid attachment, unloaded space, liquid or block collision";
        if(!attachmentValid()) return false;
        // Column provenance protects natural growth. Manual, terrain-free enclosures instead use
        // the actual empty volume, mining permissions and placement/admission events below.
        placementFailure = "construction protection rejects candidate";
        if((!GeminiSpawning.manualAdmission(parent) && WretchCamps.hasPlayerPlacement(worldObj, boundingBox.expand(1, 0, 1)))
                || GardenerNests.overlapsStructure(worldObj, boundingBox.expand(1, 1, 1))) return false;
        // Air only, including the roots' footprint. Never overwrite tiles, plants, doors or furnishings.
        for(int x = MathHelper.floor_double(boundingBox.minX); x <= MathHelper.floor_double(boundingBox.maxX); x++)
            for(int z = MathHelper.floor_double(boundingBox.minZ); z <= MathHelper.floor_double(boundingBox.maxZ); z++)
                for(int y = MathHelper.floor_double(posY); y < MathHelper.ceiling_double_int(boundingBox.maxY); y++)
                    if(!worldObj.isAirBlock(x, y, z) || worldObj.getTileEntity(x, y, z) != null
                            || !worldObj.canMineBlock(FakePlayerFactory.getMinecraft((WorldServer) worldObj), x, y, z)) return false;
        placementFailure = "entity collision prevents placement";
        for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox)) {
            Entity other = (Entity) entry;
            if(other == victim || other.isDead || other instanceof EntityGardenerTentacle && ((EntityGardenerTentacle) other).owner() == parent) continue;
            if(other.canBeCollidedWith() || other.preventEntitySpawning || other instanceof EntityGardenerCocoon) return false;
        }
        placementFailure = null;
        return true;
    }
    public Vec3 victimGoal(Entity victim) {
        return Vec3.createVectorHelper(posX, posY + 0.25D + victim.height * 0.5D, posZ);
    }
    /** One bounded search per capture, never per tick. Conservative swept-box clearance needs no pathfinder. */
    public static EntityGardenerCocoon findCeiling(EntityGardener parent, Entity victim) {
        if(!suitable(victim) || parent.isOcean()) return null;
        if(!admission(parent)) return null;
        World world = parent.worldObj;
        int cx = MathHelper.floor_double(victim.posX), cz = MathHelper.floor_double(victim.posZ);
        EntityGardenerCocoon candidate = new EntityGardenerCocoon(world);
        String rejected = "no reachable solid attachment with clear transport space";
        int top = Math.min(world.getHeight() - 2, MathHelper.floor_double(parent.boundingBox.maxY + parent.attackReach()));
        for(int radius = 0; radius <= 4; radius++) for(int dx = -radius; dx <= radius; dx++) for(int dz = -radius; dz <= radius; dz++) {
            if(Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int x = cx + dx, z = cz + dz;
            for(int y = MathHelper.floor_double(victim.boundingBox.maxY) + 2; y <= top; y++) {
                candidate.setPosition(x + 0.5D, y - 3D, z + 0.5D);
                Vec3 goal = candidate.victimGoal(victim);
                if(!parent.canTransportTo(goal)) continue;
                if(!candidate.attachmentValid()) {
                    if(GardenerNests.loaded(world, candidate.boundingBox.expand(1, 1, 1)) && naturalSupport(world, x, y, z, true))
                        rejected = "collision or liquid prevents ceiling placement";
                    continue;
                }
                AxisAlignedBB path = victim.boundingBox.addCoord(goal.xCoord - victim.posX,
                        goal.yCoord - victim.boundingBox.minY - victim.height * 0.5D, goal.zCoord - victim.posZ);
                if(!GardenerNests.loaded(world, path.expand(1, 0, 1)) || !world.getCollidingBoundingBoxes(victim, path).isEmpty()
                        || world.isAnyLiquid(path)) { rejected = "collision or unloaded space blocks transport"; continue; }
                if(!candidate.site(parent, victim)) { rejected = candidate.placementFailure; continue; }
                return candidate;
            }
        }
        failure(parent, "no valid ceiling found: " + rejected);
        return null;
    }
    /** Only a confirmed lethal feeding bite supplies surface biomass; no saved victim can be restored. */
    public static void afterFeeding(EntityGardener parent, Entity victim) {
        if(parent.isOcean()) return;
        if(!suitable(victim) || victim.isEntityAlive()) {
            failure(parent, "feeding death does not qualify for conversion"); return;
        }
        if(victim.getEntityData().getBoolean("GardenerConsumed")) return;
        parent.queueSurfaceCocoon(victim);
    }
    /** Bounded retry; the saved job contains biomass identity/position, never a restorable victim. */
    static boolean placeSurface(EntityGardener parent, Entity victim, UUID victimId, double px, double py, double pz, boolean diagnose) {
        EntityGardenerCocoon cocoon = new EntityGardenerCocoon(parent.worldObj);
        cocoon.entityUniqueID = UUID.nameUUIDFromBytes(("gardener-surface:" + victimId).getBytes(StandardCharsets.UTF_8));
        cocoon.form(false);
        if(GeminiPopulation.hasCocoon(parent.worldObj, cocoon.getUniqueID())
                || GeminiPopulation.find(parent.worldObj, outputIdentity(cocoon.getUniqueID())) != null) return true;
        if(!enabled(parent)) { if(diagnose) admission(parent); return false; }
        int top = Math.min(parent.worldObj.getHeight() - 3, MathHelper.floor_double(Math.max(py, parent.posY + 1)));
        int bottom = Math.max(2, MathHelper.floor_double(parent.posY) - 6);
        String rejected = "no suitable ground in loaded search area";
        // Search beyond the 5.8-block-wide parent's body rather than forcing overlap with the corpse.
        for(int radius = 0; radius <= 8; radius++) for(int dx = -radius; dx <= radius; dx++) for(int dz = -radius; dz <= radius; dz++) {
            if(Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            int x = MathHelper.floor_double(px) + dx, z = MathHelper.floor_double(pz) + dz;
            // Job removal and cocoon creation are saved by the same chunk.
            if((x >> 4) != (MathHelper.floor_double(parent.posX) >> 4) || (z >> 4) != (MathHelper.floor_double(parent.posZ) >> 4)) continue;
            for(int y = top; y >= bottom; y--) {
                cocoon.setPosition(x + 0.5D, y, z + 0.5D);
                if(!cocoon.site(parent, victim)) { if(cocoon.attachmentValid()) rejected = cocoon.placementFailure; continue; }
                return cocoon.place(parent, victim, false, diagnose);
            }
        }
        if(diagnose) failure(parent, "surface placement deferred: " + rejected);
        return false;
    }
    public boolean attach(EntityGardener parent, Entity victim) {
        if(!suitable(victim) || !victim.isEntityAlive() || !parent.isHolding(victim)
                || (MathHelper.floor_double(victim.posX) >> 4) != (MathHelper.floor_double(posX) >> 4)
                || (MathHelper.floor_double(victim.posZ) >> 4) != (MathHelper.floor_double(posZ) >> 4)
                || victimGoal(victim).squareDistanceTo(Vec3.createVectorHelper(victim.posX,
                        victim.boundingBox.minY + victim.height * 0.5D, victim.posZ)) > 0.16D) return false;
        return place(parent, victim, true, true);
    }
    private boolean place(EntityGardener parent, Entity victim, boolean consume, boolean diagnose) {
        if(worldObj.isRemote) return false;
        if(!enabled(parent)) { if(diagnose) admission(parent); return false; }
        if(!site(parent, victim)) { if(diagnose) failure(parent, placementFailure); return false; }
        // Honor the existing cancelable mob-attack protection hook before a non-damaging enclosure.
        if(consume && MinecraftForge.EVENT_BUS.post(new LivingAttackEvent((EntityLiving) victim, DamageSource.causeMobDamage(parent), 0F))) {
            if(diagnose) failure(parent, "attachment canceled by attack protection"); return false;
        }
        if(MinecraftForge.EVENT_BUS.post(new PlacementEvent(parent, victim, this))) {
            if(diagnose) failure(parent, "entity creation canceled by cocoon placement event"); return false;
        }
        if(isDead || !enabled(parent) || !site(parent, victim) || consume && (!victim.isEntityAlive() || !parent.isHolding(victim))) {
            if(diagnose) failure(parent, "placement invalidated during protection callbacks"); return false;
        }
        duration = remaining = GeminiConfig.gardenerCocoonSeconds * 20;
        manual = GeminiSpawning.isManual(parent);
        if(!worldObj.spawnEntityInWorld(this)) {
            if(diagnose) failure(parent, "entity creation canceled by entity admission"); super.setDead(); return false;
        }
        // Source and structure occupy the same chunk at handoff. Failed admission never consumes prey.
        if(isDead || consume && (!victim.isEntityAlive() || !parent.isHolding(victim))) {
            if(diagnose) failure(parent, "entity creation invalidated by admission callbacks"); super.setDead(); return false;
        }
        if(consume) {
            victim.setDead();
            if(!victim.isDead) { super.setDead(); return false; }
        }
        sealed = true;
        return true;
    }
    @Cancelable public static final class PlacementEvent extends Event {
        public final EntityGardener gardener;
        /** Null only for saved surface biomass whose dead source entity is no longer available. */
        public final Entity victim;
        public final EntityGardenerCocoon cocoon;
        PlacementEvent(EntityGardener gardener, Entity victim, EntityGardenerCocoon cocoon) {
            this.gardener = gardener; this.victim = victim; this.cocoon = cocoon;
        }
    }
    private boolean active() {
        return GeminiInvasionManager.hasMarsVisited(worldObj) && worldObj.difficultySetting != EnumDifficulty.PEACEFUL
                && (manual || GeminiConfig.gardenerEnabled && GeminiInvasionManager.canActivityOccur(worldObj, 3));
    }
    @Override public void onUpdate() {
        super.onUpdate(); motionX = motionY = motionZ = 0;
        if(worldObj.isRemote) {
            if((height > 2F) != isHanging()) {
                setSize(isHanging() ? 1.5F : 2F, isHanging() ? 3F : 1F);
                setPosition(posX, posY, posZ);
            }
            return;
        }
        if(!sealed) { super.setDead(); return; }
        if(completed) {
            if(++collapse >= 30) super.setDead();
            else if(collapse % 5 == 0) dataWatcher.updateObject(22, (byte) (collapse + 1));
            return;
        }
        if(!GardenerNests.loaded(worldObj, boundingBox.expand(1, 1, 1))) return;
        if(ticksExisted % 20 == 1 && !attachmentValid()) { setDead(); return; }
        if(!active()) return;
        if(remaining > 0) remaining--;
        if(ticksExisted % 40 == 0 || remaining == 0)
            dataWatcher.updateObject(21, (byte) Math.min(100, (duration - remaining) * 100L / Math.max(1, duration)));
        if(remaining > 0 || resolving) return;
        if(retry > 0) { retry--; return; }
        retry = 100;
        if(!attachmentValid()) { setDead(); return; }
        hatch();
    }
    private void hatch() {
        resolving = true;
        try {
            // Stable across retries/reloads, but distinct from the briefly coexisting empty shell.
            UUID outputId = outputIdentity(getUniqueID());
            if(GeminiPopulation.find(worldObj, outputId) != null) { finish(); return; }
            if(!worldObj.getGameRules().getGameRuleBooleanValue("doMobSpawning") || !WoodcrawlerSpawning.canConvert(worldObj)) return;
            EntityWoodcrawler output = new EntityWoodcrawler(worldObj);
            NBTTagCompound identity = new NBTTagCompound();
            identity.setLong("UUIDMost", outputId.getMostSignificantBits());
            identity.setLong("UUIDLeast", outputId.getLeastSignificantBits());
            output.inheritConversionIdentity(identity);
            output.setCrouching(true);
            output.crouchBlend = output.prevCrouchBlend = 1;
            // At most 350 loaded probes, only once per five seconds; never split the chunk-save handoff.
            for(int level = 0; level < 14; level++) for(int i = 0; i < 25; i++) {
                double x = MathHelper.floor_double(posX) + i % 5 - 2 + 0.5D;
                double z = MathHelper.floor_double(posZ) + i / 5 - 2 + 0.5D;
                double y = MathHelper.floor_double(posY) + 1 - level;
                if((MathHelper.floor_double(x) >> 4) != (MathHelper.floor_double(posX) >> 4)
                        || (MathHelper.floor_double(z) >> 4) != (MathHelper.floor_double(posZ) >> 4) || y < 1) continue;
                output.setLocationAndAngles(x, y, z, rotationYaw, 0);
                if(!GardenerNests.loaded(worldObj, output.boundingBox.expand(1, 1, 1))
                        || !World.doesBlockHaveSolidTopSurface(worldObj, MathHelper.floor_double(x), (int) y - 1, MathHelper.floor_double(z))
                        || !worldObj.getCollidingBoundingBoxes(output, output.boundingBox).isEmpty() || worldObj.isAnyLiquid(output.boundingBox)) continue;
                // A ceiling cocoon must not hatch through an intervening floor into another room.
                if(worldObj.func_147447_a(Vec3.createVectorHelper(posX, posY + 0.5D, posZ),
                        Vec3.createVectorHelper(x, y + 0.5D, z), false, true, false) != null) continue;
                boolean occupied = false;
                for(Object entry : worldObj.getEntitiesWithinAABBExcludingEntity(this, output.boundingBox)) {
                    Entity other = (Entity) entry;
                    if(!other.isDead && (other.preventEntitySpawning || other.canBeCollidedWith())) { occupied = true; break; }
                }
                if(occupied) continue;
                if(worldObj.spawnEntityInWorld(output)) finish();
                return; // Protection vetoes wait for the next retry, rather than trying every coordinate.
            }
        } finally { resolving = false; }
    }
    private static UUID outputIdentity(UUID cocoonId) {
        return UUID.nameUUIDFromBytes(("gardener-woodcrawler:" + cocoonId).getBytes(StandardCharsets.UTF_8));
    }
    private void finish() { completed = true; dataWatcher.updateObject(22, (byte) 1); }
    @Override public boolean attackEntityFrom(DamageSource source, float amount) {
        if(worldObj.isRemote || !sealed || isDead || resolving || amount <= 0 || Float.isNaN(amount)
                || source.getEntity() instanceof IGeminiCreature) return false;
        setDead(); return true;
    }
    @Override public void setDead() {
        // Destroying biomass destroys its contents. There is never a second saved living victim to release.
        if(resolving) return;
        completed = true; super.setDead();
    }
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("Hanging", isHanging()); nbt.setBoolean("Sealed", sealed);
        nbt.setBoolean("Completed", completed); nbt.setBoolean("Manual", manual);
        nbt.setInteger("Remaining", remaining); nbt.setInteger("Duration", duration); nbt.setInteger("Collapse", collapse);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) {
        form(nbt.getBoolean("Hanging")); sealed = nbt.getBoolean("Sealed");
        completed = nbt.getBoolean("Completed"); manual = nbt.getBoolean("Manual");
        duration = MathHelper.clamp_int(nbt.getInteger("Duration"), 20, 72000);
        remaining = MathHelper.clamp_int(nbt.getInteger("Remaining"), 0, duration);
        collapse = MathHelper.clamp_int(nbt.getInteger("Collapse"), 0, 30); retry = 20;
        dataWatcher.updateObject(21, (byte) ((duration - remaining) * 100L / duration));
        dataWatcher.updateObject(22, (byte) (completed ? 1 : 0));
    }
}
