package com.hbm.entity.mob.colossus;

import java.util.ArrayDeque;
import com.hbm.config.ColossusConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.BlockEvent;

/** Bounded contact volumes. Queues keep cursors, never a collection of every block in a crater. */
public final class ColossusTerrain {
    private final EntityPrismarineColossus boss;
    private final ArrayDeque<Contact> queue = new ArrayDeque<Contact>();
    private static World suppression;
    public ColossusTerrain(EntityPrismarineColossus boss) { this.boss = boss; }
    public static boolean suppressing(World w) { return suppression == w; }
    public void contact(AxisAlignedBB box) {
        if(ColossusConfig.terrain && queue.size() < 32) queue.add(new Contact(box));
    }
    public void clear() { queue.clear(); }
    public void tick() {
        World w = boss.worldObj;
        if(!ColossusConfig.terrain || !w.getGameRules().getGameRuleBooleanValue("mobGriefing")) { clear(); return; }
        ColossusEncounters ledger = ColossusEncounters.get(w);
        while(!queue.isEmpty() && ledger.spend(w.getTotalWorldTime())) {
            Contact c = queue.peek();
            int index = c.cursor++, x = c.x + index % c.sx, y = c.y + index / c.sx % c.sy, z = c.z + index / (c.sx*c.sy);
            if(c.cursor >= c.sx*c.sy*c.sz || w.getTotalWorldTime() - c.tick > 100) queue.remove();
            if(y<1 || y>254 || !EntityPrismarineColossus.loaded(w, x, z, 1)) continue;
            Block block = w.getBlock(x,y,z); int meta = w.getBlockMetadata(x,y,z);
            if(block == Blocks.air || block == Blocks.bedrock || block == Blocks.command_block || block == Blocks.portal || block == Blocks.end_portal || block == Blocks.end_portal_frame || block.getMaterial().isLiquid()) continue;
            if(block.hasTileEntity(meta) || w.getTileEntity(x,y,z) != null || block.getBlockHardness(w,x,y,z)<0 || ColossusConfig.protectedBlocks.contains(Block.blockRegistry.getNameForObject(block)) || !block.canEntityDestroy(w,x,y,z,boss)) continue;
            AxisAlignedBB collision = block.getCollisionBoundingBoxFromPool(w,x,y,z);
            if(collision != null && !collision.intersectsWith(c.box)) continue;
            EntityPlayerMP actor = FakePlayerFactory.getMinecraft((net.minecraft.world.WorldServer)w);
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(x,y,z,w,block,meta,actor);
            if(MinecraftForge.EVENT_BUS.post(event)) continue;
            suppression = w;
            try { w.setBlock(x,y,z,Blocks.air,0,2); } finally { suppression = null; }
        }
    }
    private final class Contact {
        final AxisAlignedBB box;
        final int x,y,z,sx,sy,sz;
        final long tick;
        int cursor;
        Contact(AxisAlignedBB b) {
            box=b.copy(); x=MathHelper.floor_double(b.minX); y=MathHelper.floor_double(b.minY); z=MathHelper.floor_double(b.minZ);
            sx=Math.max(1,MathHelper.ceiling_double_int(b.maxX)-x); sy=Math.max(1,MathHelper.ceiling_double_int(b.maxY)-y); sz=Math.max(1,MathHelper.ceiling_double_int(b.maxZ)-z);
            tick=boss.worldObj.getTotalWorldTime();
        }
    }
}
