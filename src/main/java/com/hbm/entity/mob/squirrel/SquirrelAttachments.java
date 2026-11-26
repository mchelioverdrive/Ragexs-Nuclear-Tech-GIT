package com.hbm.entity.mob.squirrel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

/** Server-thread queries for an entire save, including attachments in unloaded chunks. */
public final class SquirrelAttachments extends WorldSavedData {
    private static final String NAME = "rntSquirrelAttachments";
    private final Map<UUID, PlayerStats> players = new HashMap<>();
    private final Map<UUID, Attachment> active = new HashMap<>();

    public SquirrelAttachments(String name) {
        super(name);
    }

    public static SquirrelAttachments get(World world) {
        if(world.isRemote) throw new IllegalArgumentException("Squirrel attachment data is server-side");
        World overworld = DimensionManager.getWorld(0);
        if(overworld == null) overworld = world;
        SquirrelAttachments data = (SquirrelAttachments) overworld.mapStorage.loadData(SquirrelAttachments.class, NAME);
        if(data == null) {
            data = new SquirrelAttachments(NAME);
            overworld.mapStorage.setData(NAME, data);
        }
        return data;
    }

    public long getSuccessCount(UUID player) {
        PlayerStats stats = players.get(player);
        return stats == null ? 0L : stats.successes;
    }

    /** Trees used across all successful attachments, including ones since released. */
    public Set<Tree> getTrees(UUID player) {
        PlayerStats stats = players.get(player);
        return stats == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(stats.trees));
    }

    public Attachment getAttachment(UUID squirrel) {
        return active.get(squirrel);
    }

    public List<Attachment> getAttachments(UUID player) {
        List<Attachment> result = new ArrayList<>();
        for(Attachment attachment : active.values()) if(attachment.player.equals(player)) result.add(attachment);
        return Collections.unmodifiableList(result);
    }

    void record(Attachment attachment) {
        PlayerStats stats = players.computeIfAbsent(attachment.player, ignored -> new PlayerStats());
        if(stats.successes < Long.MAX_VALUE) stats.successes++;
        stats.trees.add(attachment.tree);
        active.put(attachment.squirrel, attachment);
        markDirty();
    }

    void restore(Attachment attachment) {
        Attachment previous = active.put(attachment.squirrel, attachment);
        if(previous == null || !previous.write().equals(attachment.write())) markDirty();
    }

    void remove(UUID squirrel) {
        if(active.remove(squirrel) != null) markDirty();
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        players.clear();
        active.clear();
        NBTTagList scores = tag.getTagList("Players", 10);
        for(int i = 0; i < scores.tagCount(); i++) {
            NBTTagCompound score = scores.getCompoundTagAt(i);
            UUID player = readUuid(score, "Player");
            if(player == null) continue;
            PlayerStats stats = new PlayerStats();
            stats.successes = Math.max(0, score.getLong("Successes"));
            NBTTagList trees = score.getTagList("Trees", 10);
            for(int j = 0; j < trees.tagCount(); j++) stats.trees.add(Tree.read(trees.getCompoundTagAt(j)));
            players.put(player, stats);
        }
        NBTTagList attachments = tag.getTagList("Active", 10);
        for(int i = 0; i < attachments.tagCount(); i++) {
            Attachment attachment = Attachment.read(attachments.getCompoundTagAt(i));
            if(attachment != null) active.put(attachment.squirrel, attachment);
        }
    }

    @Override public void writeToNBT(NBTTagCompound tag) {
        NBTTagList scores = new NBTTagList();
        for(Map.Entry<UUID, PlayerStats> entry : players.entrySet()) {
            NBTTagCompound score = new NBTTagCompound();
            score.setString("Player", entry.getKey().toString());
            score.setLong("Successes", entry.getValue().successes);
            NBTTagList trees = new NBTTagList();
            for(Tree tree : entry.getValue().trees) trees.appendTag(tree.write());
            score.setTag("Trees", trees);
            scores.appendTag(score);
        }
        tag.setTag("Players", scores);
        NBTTagList attachments = new NBTTagList();
        for(Attachment attachment : active.values()) attachments.appendTag(attachment.write());
        tag.setTag("Active", attachments);
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        try {
            return UUID.fromString(tag.getString(key));
        } catch(IllegalArgumentException ex) {
            return null;
        }
    }

    private static final class PlayerStats {
        long successes;
        final Set<Tree> trees = new HashSet<>();
    }

    public static final class Tree {
        public final int dimension, x, y, z;

        private Tree(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static Tree find(World world, int x, int y, int z) {
            ChunkCoordinates base = new ChunkCoordinates(x, y, z);
            ArrayDeque<ChunkCoordinates> open = new ArrayDeque<>();
            Set<ChunkCoordinates> checked = new HashSet<>();
            open.add(base);
            checked.add(base);
            // Adjacent logs share a tree key, including the four columns of a large trunk.
            int remaining = 512;
            while(!open.isEmpty() && remaining-- > 0) {
                ChunkCoordinates log = open.removeFirst();
                if(log.posY < base.posY || log.posY == base.posY && (log.posX < base.posX
                        || log.posX == base.posX && log.posZ < base.posZ)) base = log;
                for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                    ChunkCoordinates next = new ChunkCoordinates(log.posX + direction.offsetX,
                            log.posY + direction.offsetY, log.posZ + direction.offsetZ);
                    if(Math.abs(next.posX - x) <= 12 && Math.abs(next.posZ - z) <= 12 && checked.add(next)
                            && SquirrelHabitat.isLog(world, next.posX, next.posY, next.posZ)) open.addLast(next);
                }
            }
            return new Tree(world.provider.dimensionId, base.posX, base.posY, base.posZ);
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Dimension", dimension);
            tag.setInteger("X", x);
            tag.setInteger("Y", y);
            tag.setInteger("Z", z);
            return tag;
        }

        private static Tree read(NBTTagCompound tag) {
            return new Tree(tag.getInteger("Dimension"), tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        }

        @Override public boolean equals(Object other) {
            if(!(other instanceof Tree)) return false;
            Tree tree = (Tree) other;
            return dimension == tree.dimension && x == tree.x && y == tree.y && z == tree.z;
        }

        @Override public int hashCode() {
            return Objects.hash(dimension, x, y, z);
        }
    }

    public static final class Attachment {
        public final UUID player, squirrel;
        public final int dimension, x, y, z;
        public final ForgeDirection face;
        public final Tree tree;
        public final long worldTick;
        final String log;
        final int metadata;

        Attachment(EntitySquirrel squirrel, UUID player, int x, int y, int z, ForgeDirection face) {
            this(player, squirrel.getUniqueID(), squirrel.worldObj.provider.dimensionId, x, y, z, face,
                    Tree.find(squirrel.worldObj, x, y, z), squirrel.worldObj.getTotalWorldTime(),
                    Block.blockRegistry.getNameForObject(squirrel.worldObj.getBlock(x, y, z)),
                    squirrel.worldObj.getBlockMetadata(x, y, z));
        }

        private Attachment(UUID player, UUID squirrel, int dimension, int x, int y, int z, ForgeDirection face,
                Tree tree, long worldTick, String log, int metadata) {
            this.player = player;
            this.squirrel = squirrel;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
            this.tree = tree;
            this.worldTick = worldTick;
            this.log = log;
            this.metadata = metadata;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Player", player.toString());
            tag.setString("Squirrel", squirrel.toString());
            tag.setInteger("Dimension", dimension);
            tag.setInteger("X", x);
            tag.setInteger("Y", y);
            tag.setInteger("Z", z);
            tag.setByte("Face", (byte) face.ordinal());
            tag.setTag("Tree", tree.write());
            tag.setLong("WorldTick", worldTick);
            tag.setString("Log", log);
            tag.setInteger("Meta", metadata);
            return tag;
        }

        static Attachment read(NBTTagCompound tag) {
            UUID player = readUuid(tag, "Player");
            UUID squirrel = readUuid(tag, "Squirrel");
            ForgeDirection face = ForgeDirection.getOrientation(tag.getByte("Face"));
            int y = tag.getInteger("Y");
            String log = tag.getString("Log");
            if(player == null || squirrel == null || !tag.hasKey("Face", 99) || face == ForgeDirection.UNKNOWN
                    || y < 0 || y >= 256 || !tag.hasKey("Tree", 10) || log.isEmpty()) return null;
            return new Attachment(player, squirrel, tag.getInteger("Dimension"), tag.getInteger("X"), y,
                    tag.getInteger("Z"), face, Tree.read(tag.getCompoundTag("Tree")), tag.getLong("WorldTick"),
                    log, tag.getInteger("Meta"));
        }
    }
}
