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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

@SideOnly(Side.CLIENT)
public final class ColossusClient {
    private static World world;
    private static final Map<Integer,Attachment> attached=new HashMap<Integer,Attachment>();
    public static void accept(ColossusAttachmentPacket packet,World w) {
        if(world!=w) { attached.clear();world=w; }
        if(packet.boss==0) { attached.remove(packet.passenger);return; }
        if(!Float.isFinite(packet.progress) || packet.progress<0 || (packet.side!=1 && packet.side!= -1)) return;
        if(packet.grabbed) {
            if(packet.required<1 || packet.required>16 || packet.progress>packet.required
                || !Float.isFinite(packet.anchorX) || !Float.isFinite(packet.anchorY) || !Float.isFinite(packet.anchorZ)) return;
        } else if(packet.progress>4) return;
        Attachment a=attached.get(packet.passenger);
        if(a==null || a.boss!=packet.boss || a.grabbed!=packet.grabbed || a.token!=packet.token) {
            a=new Attachment(); a.boss=packet.boss; a.progress=packet.progress; attached.put(packet.passenger,a);
        }
        a.goal=packet.progress; a.grabbed=packet.grabbed; a.side=packet.side; a.seen=w.getTotalWorldTime();
        a.token=packet.token; a.required=packet.required;
        a.anchorX=packet.anchorX; a.anchorY=packet.anchorY; a.anchorZ=packet.anchorZ;
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
            if(a.grabbed && (boss.state()!=ColossusPose.GRAB || boss.grabToken()!=a.token)) continue;
            a.progress+=(a.goal-a.progress)*0.5F;
            Vec3 p=a.grabbed?boss.grabPoint(a.side,a.anchorX,a.anchorY,a.anchorZ,passenger):boss.climbPoint(a.progress,a.side);
            // Local 1.7.10 players store eye-height posY; server/remote players store feet.
            passenger.setPosition(p.xCoord,p.yCoord+passenger.yOffset,p.zCoord);passenger.motionX=passenger.motionY=passenger.motionZ=0;passenger.fallDistance=0;
            if(passenger==mc.thePlayer) {
                // The default Dash binding shares Left Shift with Sneak in 1.7.10.
                boolean sneak=GameSettings.isKeyDown(mc.gameSettings.keyBindSneak);
                if(a.grabbed) {
                    if(a.sequence==0 || sneak!=a.keyDown) {
                        a.keyDown=sneak;
                        PacketDispatcher.wrapper.sendToServer(new ColossusControlPacket(a.boss,a.token,++a.sequence,sneak));
                    }
                } else if(mc.thePlayer.ticksExisted%3==0) {
                    int direction=mc.gameSettings.keyBindForward.getIsKeyPressed()?1:mc.gameSettings.keyBindBack.getIsKeyPressed()?-1:0;
                    PacketDispatcher.wrapper.sendToServer(new ColossusControlPacket(a.boss,direction,mc.gameSettings.keyBindJump.getIsKeyPressed(),sneak));
                }
            }
        }
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event) {
        if(event.type!=RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null || mc.theWorld!=world || mc.thePlayer==null) return;
        Attachment a=attached.get(mc.thePlayer.getEntityId());
        if(a==null || !a.grabbed) return;
        Entity root=world.getEntityByID(a.boss);
        if(!(root instanceof EntityPrismarineColossus) || !root.isEntityAlive()) return;
        EntityPrismarineColossus boss=(EntityPrismarineColossus)root;
        int ticks=boss.grabTicksLeft();
        if(boss.grabToken()!=a.token || ticks==0) return;
        String key=GameSettings.getKeyDisplayString(mc.gameSettings.keyBindSneak.getKeyCode());
        String prompt=I18n.format("colossus.escape",key,(int)a.goal,a.required);
        int x=event.resolution.getScaledWidth()/2, y=event.resolution.getScaledHeight()*2/3;
        int halfWidth=Math.max(56,mc.fontRenderer.getStringWidth(prompt)/2+6);
        Gui.drawRect(x-halfWidth,y-4,x+halfWidth,y+15,0xB0182421);
        Gui.drawRect(x-halfWidth,y+13,x-halfWidth+2*halfWidth*ticks/ColossusPose.ESCAPE_TICKS,y+15,0xFF55D3B2);
        mc.fontRenderer.drawStringWithShadow(prompt,x-mc.fontRenderer.getStringWidth(prompt)/2,y,0xE5FFF7);
    }
    private static final class Attachment {
        int boss,token,required,sequence;
        int side=1;
        float progress,goal,anchorX,anchorY,anchorZ;
        boolean grabbed,keyDown;
        long seen;
    }
}
