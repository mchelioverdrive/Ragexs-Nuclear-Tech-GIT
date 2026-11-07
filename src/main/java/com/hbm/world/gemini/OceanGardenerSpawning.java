package com.hbm.world.gemini;

import java.util.ArrayList;
import java.util.List;
import com.hbm.config.GeminiConfig;
import com.hbm.entity.mob.gemini.EntityOceanGardener;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/** Rare phase-3 deep-ocean territories. Reservations survive death/restart and never request chunks. */
public final class OceanGardenerSpawning extends WorldSavedData {
    private static final String NAME = "GeminiOceanGardeners";
    private final List<int[]> territories = new ArrayList<int[]>();
    public OceanGardenerSpawning(String name) { super(name); }
    public static boolean active(World world) {
        return GeminiConfig.oceanGardenerEnabled && GeminiInvasionManager.canActivityOccur(world, 3)
                && world.difficultySetting != EnumDifficulty.PEACEFUL;
    }
    /** Returns the first water block above the seabed, with at least 33 blocks of water overhead. */
    public static int seabed(World world, int x, int z) {
        if(!world.blockExists(x, 0, z) || world.getBlock(x, 62, z).getMaterial() != Material.water) return -1;
        for(int y = 61; y >= 4; y--) {
            Material material = world.getBlock(x, y, z).getMaterial();
            if(material == Material.water) continue;
            return material.blocksMovement() && y <= 29 ? y + 1 : -1;
        }
        return -1;
    }
    public static boolean space(EntityOceanGardener boss) {
        World world = boss.worldObj;
        AxisAlignedBB envelope = AxisAlignedBB.getBoundingBox(boss.posX - 11, boss.posY - 26, boss.posZ - 11,
                boss.posX + 11, boss.posY + 6, boss.posZ + 11);
        if(envelope.minY < 2 || envelope.maxY > 254 || !GardenerNests.loaded(world, envelope.expand(2, 0, 2))) return false;
        for(int x = MathHelper.floor_double(envelope.minX); x <= MathHelper.floor_double(envelope.maxX); x++)
            for(int z = MathHelper.floor_double(envelope.minZ); z <= MathHelper.floor_double(envelope.maxZ); z++)
                for(int y = MathHelper.floor_double(envelope.minY); y <= MathHelper.floor_double(envelope.maxY); y++)
                    if(world.getBlock(x, y, z).getMaterial() != Material.water) return false;
        return world.checkNoEntityCollision(boss.boundingBox) && world.getCollidingBoundingBoxes(boss, boss.boundingBox).isEmpty();
    }
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void join(EntityJoinWorldEvent event) {
            if(event.world.isRemote || !(event.entity instanceof EntityOceanGardener) || event.entity.addedToChunk) return;
            EntityOceanGardener boss = (EntityOceanGardener) event.entity;
            if((!GeminiSpawning.manualAdmission(boss) && (!active(event.world)
                    || !GeminiPopulation.canAdmit(event.world, EntityOceanGardener.class, boss, GeminiConfig.oceanGardenerMaxLoaded)))
                    || !space(boss)) event.setCanceled(true);
        }
        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            World world = event.world;
            if(event.phase != TickEvent.Phase.END || world.getTotalWorldTime() % 1200 != 0 || !active(world)
                    || world.playerEntities.isEmpty() || !world.getGameRules().getGameRuleBooleanValue("doMobSpawning")
                    || world.rand.nextInt(GeminiConfig.oceanGardenerChance) != 0
                    || world.rand.nextInt(100) >= GeminiInvasionManager.encounterPercent(world)
                    || !GeminiPopulation.canAdmit(world, EntityOceanGardener.class, null, GeminiConfig.oceanGardenerMaxLoaded)) return;
            OceanGardenerSpawning data = (OceanGardenerSpawning) world.mapStorage.loadData(OceanGardenerSpawning.class, NAME);
            if(data == null) { data = new OceanGardenerSpawning(NAME); world.mapStorage.setData(NAME, data); }
            if(data.territories.size() >= 128) return;
            EntityPlayer player = (EntityPlayer) world.playerEntities.get(world.rand.nextInt(world.playerEntities.size()));
            if(!player.isEntityAlive()) return;
            for(int attempt = 0; attempt < 4; attempt++) {
                double angle = world.rand.nextDouble() * Math.PI * 2D;
                int radius = 40 + world.rand.nextInt(25);
                int x = MathHelper.floor_double(player.posX + Math.cos(angle) * radius);
                int z = MathHelper.floor_double(player.posZ + Math.sin(angle) * radius);
                if(!world.blockExists(x, 0, z) || !BiomeDictionary.isBiomeOfType(world.getBiomeGenForCoords(x, z), BiomeDictionary.Type.OCEAN)
                        || data.reserved(x, z)) continue;
                int floor = seabed(world, x, z);
                if(floor < 0) continue;
                EntityOceanGardener boss = new EntityOceanGardener(world);
                boss.anchor(x + 0.5D, floor + 26D, z + 0.5D);
                if(!space(boss) || world.getClosestPlayer(boss.posX, boss.posY, boss.posZ, 28) != null) continue;
                if(world.spawnEntityInWorld(boss)) {
                    data.territories.add(new int[] {x, z}); data.markDirty(); return;
                }
            }
        }
    }
    private boolean reserved(int x, int z) {
        for(int[] site : territories) {
            double dx = (double) x - site[0], dz = (double) z - site[1];
            if(dx * dx + dz * dz < 512D * 512D) return true;
        }
        return false;
    }
    @Override public void readFromNBT(NBTTagCompound nbt) {
        territories.clear();
        NBTTagList list = nbt.getTagList("Territories", 10);
        for(int i = 0; i < Math.min(128, list.tagCount()); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            territories.add(new int[] {tag.getInteger("X"), tag.getInteger("Z")});
        }
    }
    @Override public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for(int[] site : territories) {
            NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("X", site[0]); tag.setInteger("Z", site[1]); list.appendTag(tag);
        }
        nbt.setTag("Territories", list);
    }
}
