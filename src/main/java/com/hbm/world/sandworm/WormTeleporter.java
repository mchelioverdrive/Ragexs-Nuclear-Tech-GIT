package com.hbm.world.sandworm;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/** Destination-world teleporter without portal searches, coordinate scaling, or portal construction. */
public final class WormTeleporter extends Teleporter {
    private final double x, y, z;
    public WormTeleporter(WorldServer w, double x, double y, double z) { super(w); this.x = x; this.y = y; this.z = z; }
    @Override public void placeInPortal(Entity e, double ox, double oy, double oz, float yaw) { e.setLocationAndAngles(x, y, z, yaw, 0); }
    @Override public boolean placeInExistingPortal(Entity e, double x, double y, double z, float yaw) { return false; }
    @Override public boolean makePortal(Entity e) { return false; }
    public static void transfer(EntityPlayerMP p, WorldServer destination, double x, double y, double z) {
        p.mountEntity(null);
        if(p.dimension != destination.provider.dimensionId) p.mcServer.getConfigurationManager().transferPlayerToDimension(p, destination.provider.dimensionId, new WormTeleporter(destination, x, y, z));
        p.playerNetServerHandler.setPlayerLocation(x, y, z, p.rotationYaw, 0);
        p.motionX = p.motionY = p.motionZ = 0;
        p.fallDistance = 0;
    }
}
