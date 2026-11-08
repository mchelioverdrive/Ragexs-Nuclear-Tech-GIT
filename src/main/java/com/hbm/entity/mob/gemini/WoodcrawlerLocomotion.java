package com.hbm.entity.mob.gemini;

import com.hbm.world.gemini.GardenerNests;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.BlockEvent;

/** Local, server-owned posture, wall contact and bounded physical escape steering. */
final class WoodcrawlerLocomotion {
    private final EntityWoodcrawler mob;
    private final World world;
    private final WoodcrawlerSurfaceTraversal surface;
    private double goalX, goalY, goalZ, sampledX, sampledY, sampledZ, escapeX, escapeY, escapeZ;
    private int goalAge = 100, crawlTicks, stuckTicks, escapeTicks, retryTicks, attempts, dug;
    private int digX, digY, digZ, digTicks, digDelay;
    private Block digging;
    private boolean sampled;

    WoodcrawlerLocomotion(EntityWoodcrawler mob) { this.mob = mob; world = mob.worldObj; surface = new WoodcrawlerSurfaceTraversal(mob); }

    void goal(double x, double y, double z) { goalX = x; goalY = y; goalZ = z; goalAge = 0; }
    boolean recovering() { return escapeTicks > 0; }
    boolean keepCrawling() { return crawlTicks > 0 || recovering() || surface.active(); }
    boolean traversing() { return surface.active(); }
    boolean controlsMovement() { return recovering() || surface.active(); }
    Vec3 waypoint(double x, double y, double z) { return surface.waypoint(x, y, z); }
    void move(double x, double y, double z) { surface.move(x, y, z); }
    void afterPhysics() { surface.afterPhysics(); }
    void write(NBTTagCompound tag) { surface.write(tag); }
    void read(NBTTagCompound tag) { surface.read(tag); }
    void onSurfaceFinished() {
        goalAge = 100; escapeTicks = stuckTicks = 0; sampled = false; retryTicks = 40; digging = null;
        crawlTicks = Math.max(crawlTicks, 40);
    }

    private AxisAlignedBB body(double x, double y, double z, double height) {
        double width = height <= EntityWoodcrawler.CRAWLING_HEIGHT ? EntityWoodcrawler.CRAWLING_WIDTH : EntityWoodcrawler.BODY_WIDTH;
        return AxisAlignedBB.getBoundingBox(x - width / 2D, y, z - width / 2D,
                x + width / 2D, y + height, z + width / 2D).contract(0.001D, 0.001D, 0.001D);
    }
    private boolean clear(AxisAlignedBB box) {
        return GardenerNests.loaded(world, box) && world.getCollidingBoundingBoxes(mob, box).isEmpty() && !world.isAnyLiquid(box);
    }

    /** Run before native living suffocation checks, including the first tick after an egg/load. */
    void prepareBody() {
        if(mob.isBurrowing()) return;
        if(crawlTicks > 0) crawlTicks--;
        boolean folded = mob.isCrouching();
        if(!clear(body(mob.posX, mob.posY, mob.posZ, EntityWoodcrawler.STANDING_HEIGHT))) crawlTicks = 40;
        if(goalAge < 60) {
            double dx = goalX - mob.posX, dz = goalZ - mob.posZ, distance = Math.sqrt(dx * dx + dz * dz);
            if(distance > 0.1D) {
                double x = mob.posX + dx / distance * 0.8D, z = mob.posZ + dz / distance * 0.8D;
                if(!clear(body(x, mob.posY, z, EntityWoodcrawler.STANDING_HEIGHT))
                        && clear(body(x, mob.posY, z, EntityWoodcrawler.CRAWLING_HEIGHT))) crawlTicks = 40;
            }
        }
        if(keepCrawling()) mob.setCrouching(true);
        if(folded != mob.isCrouching()) mob.getNavigator().clearPathEntity();
    }

    void tick() {
        if(!surface.active()) goalAge = Math.min(100, goalAge + 1);
        if(retryTicks > 0) retryTicks--;
        if(digDelay > 0) digDelay--;
        boolean allowed = !mob.isBurrowing() && !mob.isGrabbing() && !mob.isProbing() && !mob.isCaptureBusy()
                && (mob.getHuntState() != EntityWoodcrawler.BANG || digging != null);
        if(surface.tick(recovering() ? escapeX : goalX, recovering() ? escapeY : goalY,
                recovering() ? escapeZ : goalZ, recovering() || goalAge < 60,
                mob.isEntityAlive() && (allowed || surface.active() && !mob.isBurrowing() && !mob.isGrabbing() && !mob.isProbing()) && digging == null)) return;
        if(!allowed) {
            mob.setClimbing(false); return;
        }
        if(mob.ticksExisted % 10 == 0) {
            double dx = mob.posX - sampledX, dy = mob.posY - sampledY, dz = mob.posZ - sampledZ;
            boolean blocked = mob.isCollidedHorizontally || !clear(body(mob.posX, mob.posY, mob.posZ, mob.height));
            if(!blocked && mob.onGround && goalAge >= 60) {
                int sides = 0;
                for(int side = 0; side < 4; side++) {
                    double angle = side * Math.PI / 2D;
                    if(!clear(body(mob.posX + Math.cos(angle), mob.posY, mob.posZ + Math.sin(angle), EntityWoodcrawler.CRAWLING_HEIGHT))) sides++;
                }
                blocked = sides >= 3; // A one-block pit can trap an idle creature with no prey/route.
            }
            boolean trying = blocked || goalAge < 60 && mob.getDistanceSq(goalX, goalY, goalZ) > 2D;
            stuckTicks = sampled && trying && dx * dx + dy * dy + dz * dz < 0.04D ? Math.min(400, stuckTicks + 10) : 0;
            sampledX = mob.posX; sampledY = mob.posY; sampledZ = mob.posZ; sampled = true;
            if(stuckTicks >= 20 && !recovering() && retryTicks == 0) chooseEscape();
        }
        double tx = recovering() ? escapeX : goalX, tz = recovering() ? escapeZ : goalZ;
        double dx = tx - mob.posX, dz = tz - mob.posZ, distance = Math.sqrt(dx * dx + dz * dz);
        if(recovering()) {
            mob.getNavigator().clearPathEntity(); mob.setCrouching(true);
            if(--escapeTicks == 0 || mob.getDistanceSq(escapeX, escapeY, escapeZ) < 0.5D) {
                escapeTicks = 0; digging = null; stuckTicks = 0; retryTicks = 40;
                if(mob.getHuntState() == EntityWoodcrawler.BANG) mob.setHuntState(EntityWoodcrawler.RETREAT);
            } else if(distance > 0.05D) {
                mob.motionX = dx / distance * 0.13D; mob.motionZ = dz / distance * 0.13D;
                if(digging != null || stuckTicks >= 60) dig(dx / distance, dz / distance);
            }
        }
        mob.setClimbing(false);
    }

    private void chooseEscape() {
        double best = Double.MAX_VALUE;
        double direction = goalAge < 60 ? Math.atan2(goalZ - mob.posZ, goalX - mob.posX) : attempts * 0.785398D;
        boolean found = false;
        for(int side = 0; side < 8; side++) for(int radius = 1; radius <= 2; radius++) {
            double angle = direction + (side + attempts) * Math.PI / 4D;
            double x = MathHelper.floor_double(mob.posX + Math.cos(angle) * radius * 1.5D) + 0.5D;
            double z = MathHelper.floor_double(mob.posZ + Math.sin(angle) * radius * 1.5D) + 0.5D;
            for(int dy = -4; dy <= 4; dy++) {
                double y = MathHelper.floor_double(mob.posY) + dy;
                int bx = MathHelper.floor_double(x), by = (int) y, bz = MathHelper.floor_double(z);
                if(!world.blockExists(bx, by - 1, bz) || !world.getBlock(bx, by - 1, bz).getMaterial().blocksMovement()
                        || mob.isHazardAt(x, y, z) || !clear(body(x, y, z, EntityWoodcrawler.CRAWLING_HEIGHT))) continue;
                if(mob.getDistanceSq(x, y, z) < 1D) continue;
                if(goalAge < 60 && attempts % 3 != 2
                        && (x - goalX) * (x - goalX) + (z - goalZ) * (z - goalZ)
                        >= (mob.posX - goalX) * (mob.posX - goalX) + (mob.posZ - goalZ) * (mob.posZ - goalZ) - 0.25D) continue;
                double score = mob.getDistanceSq(x, y, z) + side * 0.2D + Math.max(0, -dy) * 0.5D;
                if(score < best) { best = score; escapeX = x; escapeY = y; escapeZ = z; found = true; }
            }
        }
        attempts++;
        if(!found) { // A sealed room still needs a local direction for protected, bounded excavation.
            escapeX = mob.posX + Math.cos(direction) * 2D; escapeY = mob.posY; escapeZ = mob.posZ + Math.sin(direction) * 2D;
        }
        escapeTicks = 200; dug = 0; digging = null; digTicks = 0; crawlTicks = 40;
    }

    private boolean diggable(int x, int y, int z, boolean protection) {
        if(!world.blockExists(x, y, z) || !world.getGameRules().getGameRuleBooleanValue("mobGriefing")) return false;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        float hardness = block.getBlockHardness(world, x, y, z);
        if(block == Blocks.air || block instanceof BlockDoor || !block.getMaterial().blocksMovement()
                || hardness < 0 || hardness > 5 || block.hasTileEntity(meta) || world.getTileEntity(x, y, z) != null
                || !block.canEntityDestroy(world, x, y, z, mob)) return false;
        if(!protection) return true;
        EntityPlayer actor = FakePlayerFactory.getMinecraft((WorldServer) world);
        return world.canMineBlock(actor, x, y, z) && !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, block, meta, actor));
    }
    private void dig(double dx, double dz) {
        if(dug >= 4 || digDelay > 0) return;
        if(digging == null) {
            for(int offset = 0; offset < 4; offset++) {
                int x = MathHelper.floor_double(mob.posX + (offset < 2 ? dx * 0.9D : 0));
                int y = MathHelper.floor_double(mob.posY) + offset % 2;
                int z = MathHelper.floor_double(mob.posZ + (offset < 2 ? dz * 0.9D : 0));
                if(!diggable(x, y, z, true)) continue;
                digging = world.getBlock(x, y, z); digX = x; digY = y; digZ = z; digTicks = 0; break;
            }
            if(digging == null) { digDelay = 20; return; }
        }
        if(world.getBlock(digX, digY, digZ) != digging || !diggable(digX, digY, digZ, false)
                || mob.getDistanceSq(digX + 0.5D, digY + 0.5D, digZ + 0.5D) > 6.25D) {
            digging = null; mob.setHuntState(EntityWoodcrawler.RETREAT); return;
        }
        mob.setHuntState(EntityWoodcrawler.BANG); mob.getLookHelper().setLookPosition(digX + 0.5D, digY + 0.5D, digZ + 0.5D, 30, 30);
        if(++digTicks % 12 == 0) world.playAuxSFX(2001, digX, digY, digZ, Block.getIdFromBlock(digging) | world.getBlockMetadata(digX, digY, digZ) << 12);
        if(digTicks < 20 + digging.getBlockHardness(world, digX, digY, digZ) * 20) return;
        if(diggable(digX, digY, digZ, true) && world.func_147480_a(digX, digY, digZ, true)) dug++;
        digging = null; digDelay = 20; mob.setHuntState(EntityWoodcrawler.RETREAT);
    }
}
