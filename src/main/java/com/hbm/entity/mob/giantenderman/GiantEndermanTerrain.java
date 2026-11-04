package com.hbm.entity.mob.giantenderman;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.hbm.config.ColossusConfig;
import com.hbm.config.GiantEndermanConfig;
import com.hbm.entity.mob.witherstorm.WitherStormTerrain;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.BlockEvent;

public final class GiantEndermanTerrain {
    private static final Map<World, Budget> BUDGETS = new WeakHashMap<>();
    private final EntityGiantEnderman boss;
    private final ArrayDeque<Contact> pending = new ArrayDeque<>();
    private final List<Cell> captured = new ArrayList<>();

    GiantEndermanTerrain(EntityGiantEnderman boss) { this.boss = boss; }

    static boolean loaded(World world, AxisAlignedBB area) {
        return world.checkChunksExist(MathHelper.floor_double(area.minX), 0, MathHelper.floor_double(area.minZ),
            MathHelper.floor_double(area.maxX), 0, MathHelper.floor_double(area.maxZ));
    }

    static boolean enabled(World world) {
        return !world.isRemote && GiantEndermanConfig.terrain && world.getGameRules().getGameRuleBooleanValue("mobGriefing");
    }

    void impact(Vec3 point, int radius, int depth, int height) {
        queue(point, Math.min(radius, GiantEndermanConfig.radius), depth, height, false);
    }

    void rip(Vec3 point, int radius) {
        captured.clear();
        queue(point, Math.min(radius, GiantEndermanConfig.radius), 1, 0, true);
    }

    private void queue(Vec3 point, int radius, int depth, int height, boolean capture) {
        if (!enabled(boss.worldObj)) return;
        if (pending.size() >= 4) pending.removeLast();
        pending.addFirst(new Contact(MathHelper.floor_double(point.xCoord), MathHelper.floor_double(point.yCoord),
            MathHelper.floor_double(point.zCoord), radius, depth, height, capture, boss.worldObj.getTotalWorldTime() + 30));
    }

    List<Cell> takeFormation() {
        pending.removeIf(contact -> contact.capture);
        List<Cell> result = new ArrayList<>(captured);
        captured.clear();
        return result;
    }

    void clear() { pending.clear(); captured.clear(); }

    void tick() {
        World world = boss.worldObj;
        if (!enabled(world)) {
            clear();
            return;
        }
        long time = world.getTotalWorldTime();
        Budget budget = BUDGETS.computeIfAbsent(world, key -> new Budget());
        if (budget.tick != time) {
            budget.tick = time;
            budget.remaining = GiantEndermanConfig.budget;
        }
        // Give multiple encounters a chance to use the shared budget.
        int allowance = Math.min(budget.remaining, Math.max(1, (GiantEndermanConfig.budget + 1) / 2));
        while (allowance > 0 && !pending.isEmpty()) {
            Contact c = pending.peek();
            if (time > c.expires || c.index >= c.count || c.capture && captured.size() >= EntityEndermanTerrain.MAX_CELLS) {
                pending.remove();
                continue;
            }
            int i = c.index++;
            int dx = i % c.span - c.radius, dz = i / c.span % c.span - c.radius;
            int dy = c.height - i / (c.span * c.span);
            allowance--;
            budget.remaining--;
            if (dx * dx + dz * dz > c.radius * c.radius) continue;
            int x = c.x + dx, y = c.y + dy, z = c.z + dz;
            if (y < 1 || y >= world.getActualHeight() || !world.blockExists(x, y, z)) continue;
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            if (c.capture && (!block.isNormalCube() || block.getRenderType() != 0)) continue;
            if (remove((WorldServer)world, boss, x, y, z, block, meta) && c.capture) {
                captured.add(new Cell(dx, dy, dz, Block.getIdFromBlock(block), meta));
            }
        }
    }

    private static boolean remove(WorldServer world, Entity source, int x, int y, int z, Block block, int meta) {
        if (block == Blocks.air || block == Blocks.bedrock || block == Blocks.command_block || block == Blocks.portal
            || block == Blocks.end_portal || block == Blocks.end_portal_frame || block.getMaterial().isLiquid()
            || block.hasTileEntity(meta) || world.getTileEntity(x, y, z) != null || block.getBlockHardness(world, x, y, z) < 0
            || ColossusConfig.protectedBlocks.contains(Block.blockRegistry.getNameForObject(block))
            || !block.canEntityDestroy(world, x, y, z, source)) return false;
        net.minecraft.entity.player.EntityPlayer actor = FakePlayerFactory.getMinecraft(world);
        if (!world.canMineBlock(actor, x, y, z) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(x, y, z, world, block, meta, actor))) return false;
        if (!enabled(world) || source.isDead || world.getBlock(x, y, z) != block || world.getBlockMetadata(x, y, z) != meta
            || world.getTileEntity(x, y, z) != null) return false;
        WitherStormTerrain.beginSuppression(world);
        try {
            return world.setBlock(x, y, z, Blocks.air, 0, 2);
        } finally {
            WitherStormTerrain.endSuppression();
        }
    }

    private static final class Budget {
        long tick = Long.MIN_VALUE;
        int remaining;
    }

    private static final class Contact {
        final int x, y, z, radius, span, height, count;
        final boolean capture;
        final long expires;
        int index;

        Contact(int x, int y, int z, int radius, int depth, int height, boolean capture, long expires) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.height = height;
            this.span = 2 * radius + 1;
            this.count = span * span * (depth + height + 1);
            this.capture = capture;
            this.expires = expires;
        }
    }

    public static final class Cell {
        public final int x, y, z, id, meta;
        Cell(int x, int y, int z, int id, int meta) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.id = id;
            this.meta = meta;
        }
    }
}
