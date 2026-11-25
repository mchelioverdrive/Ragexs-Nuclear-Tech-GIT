package com.hbm.entity.mob.colossus;

import com.hbm.config.ColossusConfig;
import com.hbm.world.gen.structure.OceanTempleGenerator;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/** The temple's one-shot spawner and the anchor for its client-side dormant model. */
public final class BlockOceanTempleCore extends BlockContainer {
    BlockOceanTempleCore() {
        super(Material.rock);
        setBlockName("ocean_temple_core").setBlockTextureName("hbm:brick_jungle_circle")
            .setBlockUnbreakable().setResistance(6000000F).setLightLevel(0.8F);
    }

    @Override public TileEntity createNewTileEntity(World world, int metadata) { return new Tile(); }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if(!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if(tile instanceof Tile) ((Tile) tile).activate(player, true);
        }
        return true;
    }

    public static final class Tile extends TileEntity {
        private boolean activated, activating;
        private float displayScale = 1F;

        public boolean isActivated() { return activated; }
        public float getDisplayScale() { return displayScale; }

        @Override public boolean canUpdate() {
            return !activated && !ColossusConfig.requireTempleInteraction && (worldObj == null || !worldObj.isRemote);
        }

        @Override
        public void updateEntity() {
            if(worldObj.isRemote || activated || ColossusConfig.requireTempleInteraction
                || !ColossusConfig.enabled || worldObj.getTotalWorldTime() % 20 != 0) return;
            EntityPlayer player = worldObj.getClosestPlayer(xCoord + 0.5, yCoord + 1, zCoord + 0.5, 5);
            if(player != null) activate(player, false);
        }

        private void activate(EntityPlayer player, boolean notify) {
            if(activating || !player.isEntityAlive() || player instanceof FakePlayer) return;
            if(activated) {
                message(player, notify, "colossus.activation.spent");
                return;
            }
            if(!EntityPrismarineColossus.progressionAllowsActivation(worldObj)) {
                message(player, notify, "colossus.activation.locked");
                return;
            }
            if(!ColossusEncounters.get(worldObj).room()) {
                message(player, notify, "colossus.activation.unavailable");
                return;
            }
            if(!OceanTempleGenerator.isCompleteAt(worldObj, xCoord, yCoord, zCoord)) {
                message(player, notify, "colossus.activation.incomplete");
                return;
            }
            EntityPrismarineColossus boss = new EntityPrismarineColossus(worldObj);
            boss.setLocationAndAngles(xCoord + 0.5, yCoord + 1, zCoord + 0.5, 0, 0);
            if(!boss.safeSpawn()) {
                message(player, notify, "colossus.activation.obstructed");
                return;
            }
            activating = true;
            try {
                if(!worldObj.spawnEntityInWorld(boss)) {
                    boss.setDead();
                    message(player, notify, "colossus.activation.unavailable");
                    return;
                }
                activated = true;
                markDirty();
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                if(ColossusConfig.volume > 0) boss.playSound("mob.irongolem.throw", ColossusConfig.volume, 0.7F);
            } finally {
                activating = false;
            }
        }

        private static void message(EntityPlayer player, boolean notify, String key) {
            if(notify) player.addChatMessage(new ChatComponentTranslation(key));
        }

        @Override public AxisAlignedBB getRenderBoundingBox() {
            return AxisAlignedBB.getBoundingBox(xCoord - 16, yCoord, zCoord - 16, xCoord + 17, yCoord + 46, zCoord + 17);
        }

        @Override
        public void readFromNBT(NBTTagCompound nbt) {
            super.readFromNBT(nbt);
            activated = nbt.getBoolean("Activated");
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {
            super.writeToNBT(nbt);
            nbt.setBoolean("Activated", activated);
        }

        @Override public Packet getDescriptionPacket() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setBoolean("Activated", activated);
            nbt.setFloat("Scale", ColossusConfig.scale);
            return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
        }

        @Override public void onDataPacket(NetworkManager network, S35PacketUpdateTileEntity packet) {
            NBTTagCompound nbt = packet.func_148857_g();
            activated = nbt.getBoolean("Activated");
            displayScale = nbt.getFloat("Scale");
        }
    }
}
