package com.hbm.world.gen.structure;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.hbm.world.dungeon.LibraryDungeon;
import com.hbm.world.generator.TimedGenerator;

import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.storage.ISaveHandler;

/** Runs real world/structure code with client classes rejected and client-only members stripped. */
public class RecordingStructureWorldTest {

    @Test public void constructionAndStorageAreServerSafe() throws Throwable { runOnServer("construction"); }
    @Test public void recordingRetainsTilesMetadataAndQueuedJobs() throws Throwable { runOnServer("recording"); }
    @Test public void legacyComponentMatchesBuilderAndClipsSlices() throws Throwable { runOnServer("component"); }

    private static void runOnServer(String method) throws Throwable {
        Set<URL> urls = new LinkedHashSet<URL>();
        for(ClassLoader loader = RecordingStructureWorldTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if(loader instanceof URLClassLoader) {
                for(URL url : ((URLClassLoader)loader).getURLs()) urls.add(url);
            }
        }
        try(ServerLoader loader = new ServerLoader(urls.toArray(new URL[0]))) {
            // A positive control: the exact class from the reported crash is unavailable here.
            try {
                loader.loadClass("net.minecraft.world.storage.SaveHandlerMP");
                fail("Client save handler was available");
            } catch(ClassNotFoundException expected) { }
            try {
                loader.loadClass(Checks.class.getName()).getMethod(method).invoke(null);
            } catch(InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    public static class ServerLoader extends LaunchClassLoader {
        public ServerLoader(URL[] urls) { super(urls); }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.client.") || name.equals("net.minecraft.world.storage.SaveHandlerMP"))
                throw new ClassNotFoundException("Client class unavailable on dedicated server: " + name);
            return super.loadClass(name, resolve);
        }

        @Override public Class<?> findClass(String name) throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.launchwrapper.")) return super.findClass(name);
            if(!name.startsWith("net.minecraft.") && !name.startsWith("net.minecraftforge.") && !name.startsWith("com.hbm."))
                return super.findClass(name);
            try(InputStream stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if(stream == null) throw new ClassNotFoundException(name);
                byte[] bytes = read(stream);
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                if(clientOnly(node.visibleAnnotations)) throw new ClassNotFoundException("Client-only class: " + name);
                node.methods.removeIf(member -> clientOnly(((MethodNode)member).visibleAnnotations));
                node.fields.removeIf(member -> clientOnly(((FieldNode)member).visibleAnnotations));
                ClassWriter writer = new ClassWriter(0);
                node.accept(writer);
                bytes = writer.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch(java.io.IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }

        private static boolean clientOnly(List<AnnotationNode> annotations) {
            if(annotations != null) for(AnnotationNode annotation : annotations) {
                if("Lcpw/mods/fml/relauncher/SideOnly;".equals(annotation.desc)) {
                    String[] side = (String[])annotation.values.get(1);
                    if("CLIENT".equals(side[1])) return true;
                }
            }
            return false;
        }
    }

    private static byte[] read(InputStream stream) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for(int count; (count = stream.read(buffer)) != -1;) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    public static class Checks {
        private static void bootstrap() {
            Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new File("."), java.util.Collections.emptyList());
            Bootstrap.func_151354_b();
        }

        public static void construction() throws Exception {
            bootstrap();
            try {
                World.class.getConstructor(ISaveHandler.class, String.class, net.minecraft.world.WorldProvider.class, WorldSettings.class, Profiler.class);
                fail("Client-only World constructor was available");
            } catch(NoSuchMethodException expected) { }
            SourceWorld source = new SourceWorld();
            RecordingStructureWorld world = new RecordingStructureWorld(source, 456L, 64, Blocks.grass, false);
            assertFalse(world.isRemote);
            assertEquals(source.getSeed(), world.getSeed());
            assertEquals(0, world.provider.dimensionId);
            assertNotSame(source.provider, world.provider);
            assertSame(source.getWorldChunkManager(), world.getWorldChunkManager());
            assertSame(source.getWorldChunkManager().getBiomeGenAt(12, 34), world.getBiomeGenForCoords(12, 34));
            assertNotSame(source.getWorldInfo(), world.getWorldInfo());
            assertNotSame(source.mapStorage, world.mapStorage);
            assertNotSame(source.perWorldStorage, world.perWorldStorage);
            assertEquals(new Random(456L).nextLong(), world.rand.nextLong());
            ISaveHandler save = world.getSaveHandler();
            assertNotSame(source.getSaveHandler(), save);
            assertNull(save.loadWorldInfo());
            assertNull(save.getChunkLoader(world.provider));
            assertNull(save.getSaveHandler());
            assertNull(save.getWorldDirectory());
            assertNull(save.getMapFileFromName("idcounts"));
            File folder = new File("RTMStructureRecording");
            boolean existed = folder.exists();
            save.checkSessionLock();
            save.saveWorldInfo(world.getWorldInfo());
            save.saveWorldInfoWithPlayer(world.getWorldInfo(), new NBTTagCompound());
            world.getUniqueDataId("recording");
            world.villageCollectionObj.markDirty();
            world.mapStorage.saveAllData();
            world.perWorldStorage.saveAllData();
            save.flush();
            assertEquals(existed, folder.exists());
            assertEquals("source", source.getWorldInfo().getWorldName());
            auditPackage();
        }

        private static void auditPackage() throws Exception {
            URL directory = Checks.class.getClassLoader().getResource("com/hbm/world/gen/structure/LegacyStructureGenerator.class");
            Path root = java.nio.file.Paths.get(directory.toURI()).getParent();
            try(Stream<Path> paths = Files.list(root)) {
                for(Path file : (Iterable<Path>)paths.filter(path -> path.toString().endsWith(".class"))::iterator) {
                    String filename = file.getFileName().toString();
                    String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                    assertFalse(filename, bytes.contains("net/minecraft/client/"));
                    assertFalse(filename, bytes.contains("net/minecraft/world/storage/SaveHandlerMP"));
                    Class<?> type = Class.forName("com.hbm.world.gen.structure." + filename.replace(".class", ""), false, Checks.class.getClassLoader());
                    type.getDeclaredConstructors();
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                }
            }
            // Check the builders and helpers selected by the component switch as well.
            for(String name : new String[] {
                    "dungeon.Radio01", "dungeon.Antenna", "dungeon.DesertAtom001", "dungeon.LibraryDungeon",
                    "dungeon.Relay", "dungeon.Satellite", "dungeon.Factory", "dungeon.Barrel", "dungeon.ArcticVault",
                    "dungeon.AncientTomb", "generator.JungleDungeon", "generator.CellularDungeon",
                    "generator.CellularDungeonFactory", "generator.TimedGenerator", "generator.DungeonToolbox"}) {
                String typeName = "com.hbm.world." + name;
                try(InputStream stream = Checks.class.getClassLoader().getResourceAsStream(typeName.replace('.', '/') + ".class")) {
                    String bytes = new String(read(stream), StandardCharsets.ISO_8859_1);
                    assertFalse(typeName, bytes.contains("net/minecraft/client/"));
                    assertFalse(typeName, bytes.contains("net/minecraft/world/storage/SaveHandlerMP"));
                }
                Class<?> type = Class.forName(typeName, false, Checks.class.getClassLoader());
                type.getDeclaredConstructors();
                type.getDeclaredMethods();
                type.getDeclaredFields();
            }
        }

        public static void recording() {
            bootstrap();
            SourceWorld source = new SourceWorld();
            RecordingStructureWorld world = new RecordingStructureWorld(source, 456L, 64, Blocks.grass, false);
            assertSame(Blocks.grass, world.getBlock(0, 63, 0));
            assertSame(Blocks.stone, world.getBlock(0, 62, 0));
            assertSame(Blocks.air, world.getBlock(0, 64, 0));
            world.setBlock(23, 64, -9, Blocks.chest, 2, 3);
            world.setBlockMetadataWithNotify(23, 64, -9, 5, 3);
            TileEntityChest chest = (TileEntityChest)world.getTileEntity(23, 64, -9);
            chest.func_145976_a("recorded chest");
            TimedGenerator.addOp(world, () -> world.setBlock(24, 65, -9, Blocks.stonebrick, 3, 3));
            assertSame(Blocks.air, world.getBlock(24, 65, -9));
            world.runQueuedJobs();
            world.notifyBlocksOfNeighborChange(23, 64, -9, Blocks.chest);
            world.scheduleBlockUpdate(23, 64, -9, Blocks.chest, 1);
            world.scheduleBlockUpdateWithPriority(23, 64, -9, Blocks.chest, 1, 0);
            world.func_147446_b(23, 64, -9, Blocks.chest, 1, 0);
            List<RecordingStructureWorld.Placement> snapshot = world.snapshot();
            assertEquals(2, snapshot.size());
            assertEquals(5, snapshot.get(0).meta);
            assertEquals("recorded chest", snapshot.get(0).tile.getString("CustomName"));
            assertEquals(23, snapshot.get(0).tile.getInteger("x"));
            assertSame(world, chest.getWorldObj());
            RecordingStructureWorld target = new RecordingStructureWorld(source, 456L, 64, Blocks.grass, false);
            RecordingStructureWorld.placeSlice(target, new StructureBoundingBox(8, 0, -24, 23, 255, -9), RecordingStructureWorld.partition(snapshot));
            assertEquals(1, target.snapshot().size());
            assertEquals(5, target.getBlockMetadata(23, 64, -9));
            assertEquals("recorded chest", ((TileEntityChest)target.getTileEntity(23, 64, -9)).getInventoryName());
            assertSame(target, target.getTileEntity(23, 64, -9).getWorldObj());
        }

        public static void component() {
            bootstrap();
            SourceWorld source = new SourceWorld();
            long seed = 998877L;
            RecordingStructureWorld expected = new RecordingStructureWorld(source, seed, 64, Blocks.grass, true);
            assertTrue(new LibraryDungeon().generate(expected, new Random(seed), 20, 30, 20));
            List<RecordingStructureWorld.Placement> layout = expected.snapshot();
            assertFalse(layout.isEmpty());
            assertSame(Blocks.stonebrick, expected.getBlock(20, 30, 20));
            assertEquals(3, expected.getBlockMetadata(20, 30, 20));
            assertNotNull(expected.getTileEntity(21, 31, 25));
            for(int repetition = 0; repetition < 2; repetition++) {
                LegacyStructureGenerator.Component component = new LegacyStructureGenerator.Component(LegacyStructureGenerator.Kind.LIBRARY, 20, 20, seed, 30);
                RecordingStructureWorld target = new RecordingStructureWorld(source, seed, 64, Blocks.grass, true);
                for(int x = 8; x <= 24; x += 16) for(int z = 8; z <= 24; z += 16) {
                    StructureBoundingBox slice = new StructureBoundingBox(x, 0, z, x + 15, 255, z + 15);
                    int previous = target.snapshot().size();
                    assertTrue(component.addComponentParts(target, new Random(repetition), slice));
                    List<RecordingStructureWorld.Placement> after = target.snapshot();
                    for(int i = previous; i < after.size(); i++) {
                        RecordingStructureWorld.Placement block = after.get(i);
                        assertTrue(slice.isVecInside(block.x, block.y, block.z));
                    }
                }
                assertEquals(layout.size(), target.snapshot().size());
                for(RecordingStructureWorld.Placement block : layout) {
                    assertSame(block.block, target.getBlock(block.x, block.y, block.z));
                    assertEquals(block.meta, target.getBlockMetadata(block.x, block.y, block.z));
                    if(block.tile != null) {
                        NBTTagCompound actual = new NBTTagCompound();
                        target.getTileEntity(block.x, block.y, block.z).writeToNBT(actual);
                        assertEquals(block.tile, actual);
                    }
                }
            }
        }
    }

    /** An unpersisted source whose chunk/block reads fail, exposing accidental live-world access. */
    public static class SourceWorld extends World {
        SourceWorld() {
            super(new RecordingSaveHandler(), "source", new WorldSettings(123L, WorldSettings.GameType.SURVIVAL, true, false, WorldType.DEFAULT), new WorldProviderSurface(), new Profiler());
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public int getHeightValue(int x, int z) { return 64; }
        @Override public Block getBlock(int x, int y, int z) { throw new AssertionError("Read source block"); }
        @Override public Chunk getChunkFromChunkCoords(int x, int z) { throw new AssertionError("Loaded source chunk"); }
    }
}
