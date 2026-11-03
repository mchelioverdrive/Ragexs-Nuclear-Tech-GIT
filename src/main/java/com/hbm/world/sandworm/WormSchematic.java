package com.hbm.world.sandworm;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable classic Alpha schematics with registry-name remapping, not runtime numeric IDs. */
public final class WormSchematic {
    public static final int SIZE = 21, HEIGHT = 10, VOLUME = SIZE * SIZE * HEIGHT;
    private static final Map<String, WormSchematic> CACHE = new HashMap<String, WormSchematic>();
    private final Block[] blocks = new Block[VOLUME];
    private final byte[] metadata;
    private WormSchematic(InputStream stream) throws java.io.IOException {
        NBTTagCompound n = CompressedStreamTools.readCompressed(stream);
        if(n.getShort("Width") != SIZE || n.getShort("Length") != SIZE || n.getShort("Height") != HEIGHT)
            throw new java.io.IOException("Sandworm template must be 21 x 10 x 21");
        byte[] ids = n.getByteArray("Blocks"), high = n.getByteArray("AddBlocks");
        metadata = n.getByteArray("Data");
        if(ids.length != VOLUME || metadata.length != VOLUME || (high.length != 0 && high.length != (VOLUME + 1) / 2))
            throw new java.io.IOException("Invalid Sandworm schematic arrays");
        if(n.getTagList("TileEntities", 10).tagCount() != 0 || n.getTagList("Entities", 10).tagCount() != 0)
            throw new java.io.IOException("Sandworm templates use encounter-owned spawns, not embedded entities");
        NBTTagCompound mapping = n.getCompoundTag("SchematicaMapping");
        Map<Integer, Block> palette = new HashMap<Integer, Block>();
        for(Object k : mapping.func_150296_c()) {
            String name = (String)k;
            Block b = (Block)Block.blockRegistry.getObject(name);
            if(b == null || (b != Blocks.air && b != WormContent.flesh && b != WormContent.guts && b != WormContent.tumor
                    && b != WormContent.blood && b != WormContent.acid && b != WormContent.saliva && b != WormContent.gas))
                throw new java.io.IOException("Unsupported Sandworm palette block " + name);
            palette.put(mapping.getShort(name) & 65535, b);
        }
        for(int i = 0; i < VOLUME; i++) {
            int id = ids[i] & 255;
            if(high.length > 0) id |= (((high[i / 2] & 255) >> ((i & 1) * 4)) & 15) << 8;
            blocks[i] = palette.get(id);
            if(blocks[i] == null) throw new java.io.IOException("Unmapped Sandworm palette index " + id);
        }
    }
    public static WormSchematic get(String kind) {
        WormSchematic s = CACHE.get(kind);
        if(s != null) return s;
        try(InputStream in = WormSchematic.class.getResourceAsStream("/assets/hbm/schematics/sandworm/" + kind + ".schematic")) {
            if(in == null) throw new java.io.IOException("Missing " + kind);
            s = new WormSchematic(in); CACHE.put(kind, s); return s;
        } catch(java.io.IOException e) { throw new IllegalStateException("Cannot load Sandworm room " + kind, e); }
    }
    public Block block(int index) { return blocks[index]; }
    public int meta(int index, int rotation) {
        int m = metadata[index] & 15;
        if(blocks[index] == WormContent.tumor) {
            // Forge/vanilla side indices: down, up, north, south, west, east.
            int[] clockwise = {0, 1, 5, 4, 2, 3};
            for(int i = 0; i < rotation; i++) m = clockwise[Math.min(5, m)];
        }
        return m;
    }
    public static int rotatedX(int x, int z, int r) { return r == 0 ? x : r == 1 ? SIZE - 1 - z : r == 2 ? SIZE - 1 - x : z; }
    public static int rotatedZ(int x, int z, int r) { return r == 0 ? z : r == 1 ? x : r == 2 ? SIZE - 1 - z : SIZE - 1 - x; }
}
