package com.hbm.render.entity.mob;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.hbm.entity.mob.colossus.ColossusPose;
import com.hbm.entity.mob.colossus.EntityPrismarineColossus;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.ColossusAttachmentPacket;
import com.hbm.packet.toserver.ColossusControlPacket;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

@SideOnly(Side.CLIENT)
public final class ColossusClient {
    private static World world;
    private static final Map<Integer,Attachment> attached=new HashMap<Integer,Attachment>();
    public static void accept(ColossusAttachmentPacket packet,World w) {
        if(world!=w) { attached.clear();world=w; }
        if(packet.boss==0) { attached.remove(packet.passenger);return; }
        if(!Float.isFinite(packet.progress) || packet.progress<0 || packet.progress>4) return;
        Attachment a=attached.get(packet.passenger);
        if(a==null || a.boss!=packet.boss) { a=new Attachment(); a.boss=packet.boss;a.progress=packet.progress;attached.put(packet.passenger,a); }
        a.goal=packet.progress;a.grabbed=packet.grabbed;a.seen=w.getTotalWorldTime();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld!=world) { attached.clear();world=mc.theWorld;return; }
        if(world==null || mc.isGamePaused()) return;
        Iterator<Map.Entry<Integer,Attachment>> it=attached.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<Integer,Attachment> e=it.next();Attachment a=e.getValue();
            Entity root=world.getEntityByID(a.boss),passenger=world.getEntityByID(e.getKey());
            if(!(root instanceof EntityPrismarineColossus) || root.isDead || !root.isEntityAlive() || passenger==null || world.getTotalWorldTime()-a.seen>20) { it.remove();continue; }
            EntityPrismarineColossus boss=(EntityPrismarineColossus)root;
            a.progress+=(a.goal-a.progress)*0.5F;
            Vec3 p=a.grabbed?boss.point(ColossusPose.RIGHT_HAND,0,-3,-0.8):boss.climbPoint(a.progress);
            passenger.setPosition(p.xCoord,p.yCoord,p.zCoord);passenger.motionX=passenger.motionY=passenger.motionZ=0;passenger.fallDistance=0;
            if(passenger==mc.thePlayer && mc.thePlayer.ticksExisted%3==0) {
                int direction=mc.gameSettings.keyBindForward.getIsKeyPressed()?1:mc.gameSettings.keyBindBack.getIsKeyPressed()?-1:0;
                PacketDispatcher.wrapper.sendToServer(new ColossusControlPacket(a.boss,direction,mc.gameSettings.keyBindJump.getIsKeyPressed(),mc.gameSettings.keyBindSneak.getIsKeyPressed()));
            }
        }
    }
    private static final class Attachment {
        int boss;
        float progress,goal;
        boolean grabbed;
        long seen;
    }
}
