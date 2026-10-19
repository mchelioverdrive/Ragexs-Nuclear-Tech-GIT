package com.hbm.tileentity.network;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hbm.blocks.network.PylonMedium;
import com.hbm.items.tool.ItemWiring;
import com.hbm.uninos.NodeNet;
import com.hbm.uninos.UniNodespace;
import com.hbm.util.fauxpointtwelve.BlockPos;

import api.hbm.energymk2.Nodespace;
import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;

/** Real item, endpoint NBT and graph code; in-memory chunks do not certify Forge harvesting or disk saves. */
public class PylonWireTest {

    @Test public void lifecycleChecksOnDedicatedServerClasses() throws Throwable {
        java.util.Set<java.net.URL> urls = new java.util.LinkedHashSet<java.net.URL>();
        for(ClassLoader current = getClass().getClassLoader(); current != null; current = current.getParent()) {
            if(current instanceof java.net.URLClassLoader) java.util.Collections.addAll(urls, ((java.net.URLClassLoader)current).getURLs());
        }
        try(com.hbm.world.gen.structure.RecordingStructureWorldTest.ServerLoader loader =
                new com.hbm.world.gen.structure.RecordingStructureWorldTest.ServerLoader(urls.toArray(new java.net.URL[0]))) {
            Class<?> junit = loader.loadClass("org.junit.runner.JUnitCore");
            Object result = junit.getMethod("runClasses", Class[].class).invoke(null, (Object)new Class<?>[] {loader.loadClass(Checks.class.getName())});
            String failures = result.getClass().getMethod("getFailures").invoke(result).toString();
            System.out.println("Dedicated-class wire lifecycle cases: " + result.getClass().getMethod("getRunCount").invoke(result) + "; " + failures);
            if(!(Boolean)result.getClass().getMethod("wasSuccessful").invoke(result)) {
                for(Object failure : (java.util.List<?>)result.getClass().getMethod("getFailures").invoke(result)) {
                    ((Throwable)failure.getClass().getMethod("getException").invoke(failure)).printStackTrace();
                }
                fail(failures);
            }
        }
    }

    public static class Checks {
    @BeforeClass public static void bootstrap() {
        Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new File("."), java.util.Collections.emptyList());
        Bootstrap.func_151354_b();
        TileEntity.addMapping(TileEntityPylon.class, "wireTestPylon");
        TileEntity.addMapping(TileEntityPylonMedium.class, "wireTestMedium");
        TileEntity.addMapping(TileEntityPylonLarge.class, "wireTestLarge");
        TileEntity.addMapping(TileEntityConnector.class, "wireTestConnector");
        TileEntity.addMapping(TileEntitySubstation.class, "wireTestSubstation");
    }

    @After public void cleanup() {
        for(World world : new ArrayList<World>(UniNodespace.worlds.keySet())) UniNodespace.unloadWorld(world);
        for(NodeNet net : new ArrayList<NodeNet>(UniNodespace.activeNodeNets)) net.destroy();
    }

    private TileEntityPylonBase pole(int type) {
        switch(type) {
        case 0: return new TileEntityConnector();
        case 1: return new TileEntityPylon();
        case 6: return new TileEntityPylonLarge();
        case 7: return new TileEntitySubstation();
        default: return new TileEntityPylonMedium(); // four registered medium designs share this native tile
        }
    }

    @Test public void everyRegisteredServicePairWorksInBothDirectionsWithoutNodes() {
        for(int a = 0; a < 8; a++) for(int b = 0; b < 8; b++) {
            WireWorld world = new WireWorld();
            TileEntityPylonBase first = world.put(pole(a), 0, 64, 0), second = world.put(pole(b), 4, 64, 0);
            int expected = (a >= 6) == (b >= 6) ? 0 : 1;
            assertEquals(a + " -> " + b, expected, TileEntityPylonBase.connect(first, second));
            assertEquals(expected == 0 ? 1 : 0, first.connected.size());
            assertEquals(first.connected.size(), second.connected.size());
            if(expected == 0) {
                assertEquals(5, TileEntityPylonBase.connect(second, first));
                first.reconcileConductorNode(); second.reconcileConductorNode();
                process(world, 100);
                assertSame(Nodespace.getNode(world, 0, 64, 0).net, Nodespace.getNode(world, 4, 64, 0).net);
            }
        }
    }

    @Test public void rangeSelfAndDuplicateFailuresDoNotPartiallyAddRecords() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylon(), 25, 64, 0);
        assertEquals(2, TileEntityPylonBase.connect(a, a));
        assertEquals(0, TileEntityPylonBase.connect(a, b));
        assertEquals(5, TileEntityPylonBase.connect(b, a));
        TileEntityPylonBase far = world.put(new TileEntityPylon(), 26, 64, 0);
        assertEquals(3, TileEntityPylonBase.connect(a, far));
        assertEquals(1, a.connected.size()); assertTrue(far.connected.isEmpty());
        TileEntityPylonBase connector = world.put(new TileEntityConnector(), 1, 64, 0);
        assertEquals(3, TileEntityPylonBase.connect(connector, b));
    }

    @Test public void branchesHaveNoNewArtificialCapacityLimitAndCleanupKeepsOtherWires() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylonMedium(), 4, 64, 0);
        TileEntityPylonBase c = world.put(new TileEntityPylon(), 8, 64, 0), d = world.put(new TileEntityPylon(), 12, 64, 0);
        assertEquals(0, TileEntityPylonBase.connect(a, b)); assertEquals(0, TileEntityPylonBase.connect(b, c));
        assertEquals(0, TileEntityPylonBase.connect(c, d));
        for(TileEntityPylonBase tile : new TileEntityPylonBase[] {a,b,c,d}) tile.reconcileConductorNode();
        process(world, 100);
        world.remove(b); b.disconnectAll(); b.invalidate();
        process(world, 100);
        assertTrue(a.connected.isEmpty()); assertTrue(b.connected.isEmpty());
        assertEquals(1, c.connected.size()); assertTrue(c.hasConnection(12, 64, 0));
        assertNotSame(Nodespace.getNode(world, 0, 64, 0).net, Nodespace.getNode(world, 8, 64, 0).net);
        assertSame(Nodespace.getNode(world, 8, 64, 0).net, Nodespace.getNode(world, 12, 64, 0).net);
        assertEquals(0, world.spawned); assertEquals(0, world.placements);
    }

    @Test public void breakingEitherEndIsIdempotent() {
        for(boolean reverse : new boolean[] {false, true}) {
            WireWorld world = new WireWorld();
            TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylon(), 4, 64, 0);
            assertEquals(0, TileEntityPylonBase.connect(a, b));
            TileEntityPylonBase removed = reverse ? b : a;
            world.remove(removed); removed.disconnectAll(); removed.invalidate();
            assertTrue(a.connected.isEmpty()); assertTrue(b.connected.isEmpty());
            assertEquals(0, world.spawned); assertEquals(0, world.placements);
        }
    }

    @Test public void repeatedOldCleanupCannotDetachReplacementWires() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase old = world.put(new TileEntityPylon(), 0,64,0), peer = world.put(new TileEntityPylon(),4,64,0);
        assertEquals(0, TileEntityPylonBase.connect(old,peer)); world.remove(old);
        TileEntityPylonBase replacement = world.put(new TileEntityPylon(),0,64,0);
        assertEquals(0, TileEntityPylonBase.connect(replacement,peer)); old.disconnectAll(); old.invalidate();
        assertEquals(1, replacement.connected.size()); assertEquals(1, peer.connected.size());
    }

    @Test public void severalPoleBreaksBeforeSplitProcessingKeepEveryDisconnectedBranchSeparate() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase[] tiles = new TileEntityPylonBase[] {
            world.put(new TileEntityPylon(),0,64,0), world.put(new TileEntityPylon(),4,64,0),
            world.put(new TileEntityPylon(),8,64,0), world.put(new TileEntityPylon(),12,64,0),
            world.put(new TileEntityPylon(),4,64,4), world.put(new TileEntityPylon(),12,64,4)};
        for(int[] edge : new int[][] {{0,1},{1,2},{2,3},{1,4},{3,5}}) assertEquals(0,TileEntityPylonBase.connect(tiles[edge[0]],tiles[edge[1]]));
        for(TileEntityPylonBase tile : tiles) tile.reconcileConductorNode(); process(world,100);
        world.remove(tiles[1]); world.remove(tiles[3]); process(world,100);
        for(int a : new int[] {0,2,4,5}) for(int b : new int[] {0,2,4,5}) {
            if(a != b) assertNotSame("branches " + a + " and " + b,
                    Nodespace.getNode(world,tiles[a].xCoord,64,tiles[a].zCoord).net,
                    Nodespace.getNode(world,tiles[b].xCoord,64,tiles[b].zCoord).net);
        }
    }

    @Test public void allowedMixedPoleGraphRetainsExactPowerAccounting() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(),0,64,0), b = world.put(new TileEntityPylonMedium(),4,64,0);
        assertEquals(0,TileEntityPylonBase.connect(a,b)); a.reconcileConductorNode(); b.reconcileConductorNode(); process(world,100);
        final long[] source = {1000}, destination = {0};
        api.hbm.energymk2.IEnergyProviderMK2 provider = new api.hbm.energymk2.IEnergyProviderMK2() {
            public boolean isLoaded() { return true; }
            public long getStoredEnergyQuanta() { return source[0]; }
            public void setStoredEnergyQuanta(long value) { source[0] = value; }
            public long getEnergyCapacityQuanta() { return 1000; }
            public long getMaxOutputQuantaPerTick() { return 400; }
        };
        api.hbm.energymk2.IEnergyReceiverMK2 receiver = new api.hbm.energymk2.IEnergyReceiverMK2() {
            public boolean isLoaded() { return true; }
            public long getStoredEnergyQuanta() { return destination[0]; }
            public void setStoredEnergyQuanta(long value) { destination[0] = value; }
            public long getEnergyCapacityQuanta() { return 500; }
        };
        api.hbm.energymk2.PowerNetMK2 net = Nodespace.getNode(world,0,64,0).net;
        net.addProvider(provider); net.addReceiver(receiver); net.update();
        assertEquals(600,source[0]); assertEquals(400,destination[0]); assertEquals(1000,source[0]+destination[0]);
    }

    @Test public void replacementOfRetainedCompactCableNodeCannotThrowDuringWiring() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(),0,64,0), b = world.put(new TileEntityPylon(),4,64,0);
        Nodespace.PowerNode compact = new Nodespace.PowerNode(new BlockPos(0,64,0)); compact.setStandardConnections(0,64,0); Nodespace.createNode(world,compact);
        assertEquals(0, TileEntityPylonBase.connect(a,b)); assertTrue(compact.expired);
        a.reconcileConductorNode(); b.reconcileConductorNode(); process(world,100);
        assertFalse(Nodespace.getNode(world,0,64,0).hasStandardConnections());
        assertSame(Nodespace.getNode(world,0,64,0).net, Nodespace.getNode(world,4,64,0).net);
    }

    @Test public void reattachmentReplacesExceptionalNodesWhosePortsDisagreeWithSavedWires() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(),0,64,0), b = world.put(new TileEntityPylon(),4,64,0);
        assertEquals(0,TileEntityPylonBase.connect(a,b)); a.reconcileConductorNode(); b.reconcileConductorNode(); process(world,100);
        Nodespace.PowerNode stale = Nodespace.getNode(world,0,64,0);
        NBTTagCompound restored = save(a); restored.setInteger("conCount",0); a.readFromNBT(restored);
        a.reconcileConductorNode(); b.reconcileConductorNode(); process(world,100);
        assertTrue(stale.expired); assertTrue(b.connected.isEmpty());
        assertNotSame(Nodespace.getNode(world,0,64,0).net,Nodespace.getNode(world,4,64,0).net);
    }

    @Test public void malformedSavedTransmissionBoundaryIsRemovedAtBothLoadedEnds() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(),0,64,0), b = world.put(new TileEntityPylonLarge(),4,64,0);
        a.connected.add(new int[] {4,64,0}); b.connected.add(new int[] {0,64,0});
        a.reconcileConductorNode(); b.reconcileConductorNode(); process(world,100);
        assertTrue(a.connected.isEmpty()); assertTrue(b.connected.isEmpty());
        assertNotSame(Nodespace.getNode(world,0,64,0).net,Nodespace.getNode(world,4,64,0).net);
    }

    @Test public void unloadDoesNotDisconnectAndRemovalJournalSurvivesReload() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylon(), 20, 64, 0);
        assertEquals(0, TileEntityPylonBase.connect(a, b));
        a.reconcileConductorNode(); b.reconcileConductorNode(); process(world, 100);
        NBTTagCompound saved = save(b);
        b.onChunkUnload(); world.chunk(1, 0).isChunkLoaded = false;
        a.reconcileConductorNode(); assertEquals(1, a.connected.size());
        world.remove(a);
        assertEquals(0, world.unloadedReads);
        NBTTagCompound journal = new NBTTagCompound(); WireRemovalData.get(world).writeToNBT(journal);
        assertEquals(1, journal.getTagList("removals", 10).tagCount());
        WireWorld loaded = new WireWorld();
        WireRemovalData.get(loaded).readFromNBT(journal);
        // Deleted start chunk stays unloaded: the journal must remove this without consulting it.
        TileEntityPylon restored = new TileEntityPylon(); restored.readFromNBT(saved); loaded.put(restored, 20, 64, 0);
        restored.reconcileConductorNode(); assertTrue(restored.connected.isEmpty());
        assertEquals(0, loaded.unloadedReads);
        NBTTagCompound after = new NBTTagCompound(); WireRemovalData.get(loaded).writeToNBT(after);
        assertEquals(0, after.getTagList("removals", 10).tagCount());
    }

    @Test public void legacyNbtIsBoundedDeduplicatedAndRetainsValidSparseRecords() {
        TileEntityPylon tile = new TileEntityPylon();
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("conCount", Integer.MAX_VALUE);
        tag.setInteger("x", 0); tag.setInteger("y", 64); tag.setInteger("z", 0);
        tag.setIntArray("con0", new int[] {4,64,0}); tag.setIntArray("con1", new int[] {4,64,0});
        tag.setIntArray("con2", new int[] {}); tag.setIntArray("con3", new int[] {0,64,0});
        tag.setIntArray("con4", new int[] {3,-1,0}); tag.setIntArray("con2147483646", new int[] {8,64,0});
        tag.setIntArray("con9999999999", new int[] {9,64,0}); tag.setString("con9", "wrong type");
        tile.readFromNBT(tag); assertEquals(2, tile.connected.size());
        TileEntityPylon loaded = new TileEntityPylon(); loaded.readFromNBT(save(tile));
        assertEquals(2, loaded.connected.size()); assertEquals(tile.getWireEndpointId(), loaded.getWireEndpointId());
        loaded.readFromNBT(save(tile)); assertEquals(2, loaded.connected.size());
    }

    @Test public void reciprocalLegacyWiresReloadOnceAndOrphansNeverRecreatePeers() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylonMedium(), 4, 64, 0);
        assertEquals(0, TileEntityPylonBase.connect(a, b));
        NBTTagCompound aTag = save(a), bTag = save(b);
        aTag.removeTag("wireEndpointMost"); aTag.removeTag("wireEndpointLeast");
        WireWorld loaded = new WireWorld();
        TileEntityPylon a2 = new TileEntityPylon(); a2.readFromNBT(aTag); loaded.put(a2, 0, 64, 0);
        TileEntityPylonMedium b2 = new TileEntityPylonMedium(); b2.readFromNBT(bTag); loaded.put(b2, 4, 64, 0);
        for(int i = 0; i < 10; i++) { a2.reconcileConductorNode(); b2.reconcileConductorNode(); process(loaded, 10); }
        assertEquals(1, a2.connected.size()); assertEquals(1, b2.connected.size());
        world.remove(a); TileEntityPylon replacement = world.put(new TileEntityPylon(), 0, 64, 0);
        b.readFromNBT(bTag); b.reconcileConductorNode();
        assertTrue(b.connected.isEmpty()); assertTrue(replacement.connected.isEmpty());
        assertEquals(0, world.placements);
    }

    @Test public void realDrumRejectsBrokenReplacedUnloadedAndCrossDimensionSelections() {
        for(int scenario = 0; scenario < 4; scenario++) {
            WireWorld world = new WireWorld();
            TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylon(), 20, 64, 0);
            ItemWiring drum = new ItemWiring(); ItemStack stack = new ItemStack(drum); Player player = new Player(world);
            use(drum, stack, player, world, a);
            assertTrue(stack.getTagCompound().hasKey("wireSelection", 10));
            if(scenario == 0) world.remove(a);
            if(scenario == 1) { world.remove(a); world.put(new TileEntityPylon(), 0, 64, 0); }
            if(scenario == 2) world.chunk(0, 0).isChunkLoaded = false;
            if(scenario == 3) stack.getTagCompound().getCompoundTag("wireSelection").setInteger("dimension", 1);
            use(drum, stack, player, world, b);
            assertTrue(b.connected.isEmpty()); assertFalse(stack.hasTagCompound());
            assertTrue(player.message.contains("Start pole")); assertEquals(0, world.unloadedReads);
        }
    }

    @Test public void realDrumOnlyStoresTemporarySelectionAndPreservesOtherItemTags() {
        WireWorld world = new WireWorld();
        TileEntityPylonBase a = world.put(new TileEntityPylon(), 0, 64, 0), b = world.put(new TileEntityPylonMedium(), 4, 64, 0);
        ItemWiring drum = new ItemWiring(); ItemStack stack = new ItemStack(drum); Player player = new Player(world);
        stack.setTagCompound(new NBTTagCompound()); stack.getTagCompound().setString("custom", "keep");
        use(drum, stack, player, world, a); use(drum, stack, player, world, b);
        assertEquals("Wire connected", player.message); assertEquals(1, a.connected.size());
        assertFalse(stack.getTagCompound().hasKey("wireSelection")); assertEquals("keep", stack.getTagCompound().getString("custom"));
        world.isRemote = true;
        use(drum, stack, player, world, a); assertFalse(stack.getTagCompound().hasKey("wireSelection"));
    }

    @Test public void malformedMultiblockActivationFindsNoCoreWithoutThrowing() {
        WireWorld world = new WireWorld(); world.chunk(0,0);
        Block block = new PylonMedium(Material.wood);
        world.blocks.put(new BlockPos(0,64,0), block); world.metadata.put(new BlockPos(0,64,0), 1);
        assertNull(TileEntityPylonBase.findPylon(world, 0,64,0));
        assertFalse(block.onBlockActivated(world,0,64,0,new Player(world),1,0,0,0));
    }

    private static void use(ItemWiring drum, ItemStack stack, Player player, WireWorld world, TileEntityPylonBase tile) {
        assertTrue(drum.onItemUse(stack, player, world, tile.xCoord, tile.yCoord, tile.zCoord, 1, 0,0,0));
    }
    private static NBTTagCompound save(TileEntityPylonBase tile) { NBTTagCompound tag = new NBTTagCompound(); tile.writeToNBT(tag); return tag; }
    private static void process(WireWorld world, int ticks) {
        try {
            Method method = UniNodespace.UniNodeWorld.class.getDeclaredMethod("processTopology", World.class, int.class); method.setAccessible(true);
            for(int i = 0; i < ticks; i++) method.invoke(UniNodespace.worlds.get(world), world, 8);
        } catch(Exception failure) { throw new AssertionError(failure); }
    }

    private static class Player extends EntityPlayer {
        String message;
        Player(World world) { super(world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "wire-test")); }
        @Override public boolean canPlayerEdit(int x,int y,int z,int side,ItemStack stack) { return true; }
        @Override public void addChatMessage(IChatComponent chat) { message = chat.getUnformattedText(); }
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return true; }
        @Override public net.minecraft.util.ChunkCoordinates getPlayerCoordinates() { return new net.minecraft.util.ChunkCoordinates(0,64,0); }
    }

    /** Missing chunk reads fail immediately; cleanup cannot place blocks or emit drops unnoticed. */
    private static class WireWorld extends World {
        final Map<Long, Chunk> chunks = new HashMap<Long, Chunk>();
        final Map<BlockPos, Block> blocks = new HashMap<BlockPos, Block>();
        final Map<BlockPos, Integer> metadata = new HashMap<BlockPos, Integer>();
        int unloadedReads, spawned, placements;
        WireWorld() {
            super((ISaveHandler)Proxy.newProxyInstance(ISaveHandler.class.getClassLoader(), new Class<?>[] {ISaveHandler.class},
                    (proxy, method, args) -> null), "wire-test", new WorldSettings(1,WorldSettings.GameType.SURVIVAL,false,false,WorldType.FLAT),
                    new WorldProviderSurface(), new Profiler());
        }
        static long key(int x,int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
        Chunk chunk(int x,int z) {
            Chunk chunk = chunks.get(key(x,z));
            if(chunk == null) { chunk = new Chunk(this,x,z); chunk.isChunkLoaded = true; chunks.put(key(x,z),chunk); }
            return chunk;
        }
        <T extends TileEntityPylonBase> T put(T tile,int x,int y,int z) {
            tile.setWorldObj(this); tile.xCoord=x; tile.yCoord=y; tile.zCoord=z;
            chunk(x>>4,z>>4).chunkTileEntityMap.put(new ChunkPosition(x&15,y,z&15),tile);
            return tile;
        }
        void remove(TileEntityPylonBase tile) {
            tile.disconnectAll(); chunk(tile.xCoord>>4,tile.zCoord>>4).chunkTileEntityMap.remove(new ChunkPosition(tile.xCoord&15,tile.yCoord,tile.zCoord&15)); tile.invalidate();
        }
        @Override protected IChunkProvider createChunkProvider() {
            return (IChunkProvider)Proxy.newProxyInstance(IChunkProvider.class.getClassLoader(), new Class<?>[] {IChunkProvider.class}, (proxy, method, args) -> {
                if(method.getName().equals("chunkExists")) { Chunk chunk = chunks.get(key((Integer)args[0],(Integer)args[1])); return chunk != null && chunk.isChunkLoaded; }
                if(method.getReturnType() == boolean.class) return false;
                if(method.getReturnType() == int.class) return 0;
                return null;
            });
        }
        @Override public Chunk getChunkFromChunkCoords(int x,int z) {
            Chunk chunk = chunks.get(key(x,z));
            if(chunk == null || !chunk.isChunkLoaded) { unloadedReads++; throw new AssertionError("Forced unloaded chunk"); }
            return chunk;
        }
        @Override public Block getBlock(int x,int y,int z) { Block block = blocks.get(new BlockPos(x,y,z)); return block == null ? net.minecraft.init.Blocks.air : block; }
        @Override public int getBlockMetadata(int x,int y,int z) { Integer meta = metadata.get(new BlockPos(x,y,z)); return meta == null ? 12 : meta; }
        @Override public void markTileEntityChunkModified(int x,int y,int z,TileEntity tile) { }
        @Override public void markBlockForUpdate(int x,int y,int z) { }
        @Override public boolean spawnEntityInWorld(Entity entity) { spawned++; return true; }
        @Override public boolean setBlock(int x,int y,int z,Block block,int meta,int flags) { placements++; return false; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
    }
    }
}
