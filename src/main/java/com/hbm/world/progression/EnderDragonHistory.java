package com.hbm.world.progression;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import com.hbm.main.MainRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTSizeTracker;

/** Reads only saved End chunks. In 1.7.10 the exit fountain can be anywhere the dragon died. */
final class EnderDragonHistory {
    enum Result { FOUND, NOT_FOUND, INCOMPLETE }

    private static final int SECTOR_BYTES = 4096;
    private static final int PORTAL_Y = 64;

    static Result findExitPortal(File end) {
        File directory = new File(end, "region");
        if(!directory.exists()) return Result.NOT_FOUND;
        File[] files = directory.listFiles((dir, name) -> name.matches("r\\.-?\\d+\\.-?\\d+\\.mca"));
        if(files == null) {
            MainRegistry.logger.warn("Could not list End region files for boss progression in {}", directory);
            return Result.INCOMPLETE;
        }
        int bedrock = Block.getIdFromBlock(Blocks.bedrock);
        int portal = Block.getIdFromBlock(Blocks.end_portal);
        Result result = Result.NOT_FOUND;
        for(File file : files) {
            Result region = scanRegion(file, bedrock, portal);
            if(region == Result.FOUND) return region;
            if(region == Result.INCOMPLETE) result = region;
        }
        return result;
    }

    private static Result scanRegion(File file, int bedrock, int portal) {
        // Vanilla RegionFile opens in read-write mode and can initialize or pad files.
        try(RandomAccessFile region = new RandomAccessFile(file, "r")) {
            if(region.length() < 2L * SECTOR_BYTES) throw new IOException("Truncated Anvil region header");
            int[] locations = new int[SECTOR_BYTES / Integer.BYTES];
            for(int i = 0; i < locations.length; i++) locations[i] = region.readInt();
            Result result = Result.NOT_FOUND;
            for(int location : locations) {
                if(location == 0) continue;
                try {
                    if(hasExitPortal(readChunk(region, location), bedrock, portal)) return Result.FOUND;
                } catch(IOException | RuntimeException e) {
                    if(result != Result.INCOMPLETE) MainRegistry.logger.warn("Could not read a saved End chunk in " + file, e);
                    result = Result.INCOMPLETE;
                }
            }
            return result;
        } catch(IOException e) {
            MainRegistry.logger.warn("Could not read End region " + file, e);
            return Result.INCOMPLETE;
        }
    }

    private static NBTTagCompound readChunk(RandomAccessFile region, int location) throws IOException {
        int sector = location >>> 8;
        int sectors = location & 255;
        long start = (long)sector * SECTOR_BYTES;
        if(sector < 2 || sectors == 0 || start + (long)sectors * SECTOR_BYTES > region.length()) {
            throw new IOException("Invalid Anvil chunk location");
        }
        region.seek(start);
        int size = region.readInt();
        if(size < 2 || size > sectors * SECTOR_BYTES - Integer.BYTES) throw new IOException("Invalid Anvil chunk length");
        int compression = region.readUnsignedByte();
        byte[] bytes = new byte[size - 1];
        region.readFully(bytes);
        ByteArrayInputStream source = new ByteArrayInputStream(bytes);
        InputStream decoded;
        if(compression == 1) decoded = new GZIPInputStream(source);
        else if(compression == 2) decoded = new InflaterInputStream(source);
        else throw new IOException("Unknown Anvil chunk compression " + compression);

        try(DataInputStream input = new DataInputStream(decoded)) {
            return CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(16L * 1024 * 1024)).getCompoundTag("Level");
        }
    }

    private static boolean hasExitPortal(NBTTagCompound chunk, int bedrock, int portal) {
        NBTTagList sections = chunk.getTagList("Sections", 10);
        NBTTagCompound base = null, fountain = null;
        for(int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound section = sections.getCompoundTagAt(i);
            int y = section.getByte("Y");
            if(y == (PORTAL_Y - 1) >> 4) base = section;
            if(y == PORTAL_Y >> 4) fountain = section;
        }
        if(base == null || fountain == null) return false;
        byte[] lower = base.getByteArray("Blocks"), lowerHigh = base.getByteArray("Add");
        byte[] upper = fountain.getByteArray("Blocks"), upperHigh = fountain.getByteArray("Add");
        if(lower.length != 4096 || upper.length != 4096) return false;

        // The vanilla exit has a central bedrock pillar and portal blocks on a bedrock floor.
        // A match fits in the center's chunk even when the fountain crosses a chunk border.
        for(int center = 0; center < 256; center++) {
            if(blockAt(upper, upperHigh, center) != bedrock || blockAt(upper, upperHigh, center + 256) != bedrock
                || blockAt(upper, upperHigh, center + 512) != bedrock || blockAt(upper, upperHigh, center + 768) != bedrock
                || blockAt(lower, lowerHigh, center + 3840) != bedrock) continue;
            int x = center & 15, z = center >> 4;
            for(int dz = -2; dz <= 2; dz++) for(int dx = -2; dx <= 2; dx++) {
                if(dx * dx + dz * dz > 6 || x + dx < 0 || x + dx > 15 || z + dz < 0 || z + dz > 15) continue;
                int tile = (z + dz) * 16 + x + dx;
                if(blockAt(upper, upperHigh, tile) == portal && blockAt(lower, lowerHigh, tile + 3840) == bedrock) return true;
            }
        }
        return false;
    }

    private static int blockAt(byte[] blocks, byte[] high, int index) {
        int id = blocks[index] & 255;
        if(high.length == 2048) id |= ((high[index >> 1] >> ((index & 1) * 4)) & 15) << 8;
        return id;
    }
}
