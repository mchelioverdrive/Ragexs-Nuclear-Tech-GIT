package com.hbm.packet.toclient;

import com.hbm.entity.mob.sandworm.EntitySandworm;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

/** Fixed-size poses, five-tick interpolation; no client-to-server encounter packets. */
public final class SandwormPosePacket implements IMessage {
    private int entity, dimension;
    private double[][] pose;
    public SandwormPosePacket() { }
    public SandwormPosePacket(EntitySandworm worm) {
        entity = worm.getEntityId(); dimension = worm.dimension;
        pose = new double[EntitySandworm.SEGMENTS][5];
        for(int i = 0; i < EntitySandworm.SEGMENTS; i++) System.arraycopy(worm.pose[i], 0, pose[i], 0, 5);
    }
    @Override public void fromBytes(ByteBuf b) {
        if(b.readableBytes() != 8 + 24 + EntitySandworm.SEGMENTS * 20) throw new IllegalArgumentException("Invalid Sandworm pose size");
        dimension = b.readInt(); entity = b.readInt(); pose = EntitySandworm.readPose(b);
    }
    @Override public void toBytes(ByteBuf b) {
        b.writeInt(dimension); b.writeInt(entity);
        for(int j = 0; j < 3; j++) b.writeDouble(pose[0][j]);
        for(int i = 0; i < EntitySandworm.SEGMENTS; i++) for(int j = 0; j < 5; j++) b.writeFloat((float)(j < 3 ? pose[i][j] - pose[0][j] : pose[i][j]));
    }
    public static final class Handler implements IMessageHandler<SandwormPosePacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(final SandwormPosePacket packet, final MessageContext ctx) {
            final Minecraft mc = Minecraft.getMinecraft(); final World world = mc.theWorld;
            mc.func_152344_a(new Runnable() { @Override public void run() {
                if(world == null || mc.theWorld != world || mc.getNetHandler() != ctx.getClientHandler() || world.provider.dimensionId != packet.dimension) return;
                Entity e = world.getEntityByID(packet.entity);
                if(e instanceof EntitySandworm) ((EntitySandworm)e).acceptPose(packet.pose, false);
            }});
            return null;
        }
    }
}
