package com.hbm.render.entity.mob;

import java.nio.DoubleBuffer;
import com.hbm.entity.mob.colossus.ColossusPose;
import com.hbm.entity.mob.colossus.EntityPrismarineColossus;
import com.hbm.render.model.ModelPrismarineColossus;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class RenderPrismarineColossus extends Render {
    public static final ResourceLocation SKIN=new ResourceLocation("hbm","textures/entity/prismarine_colossus.png");
    private final ModelPrismarineColossus model=new ModelPrismarineColossus();
    private final ColossusPose pose=new ColossusPose();
    private final DoubleBuffer matrix=BufferUtils.createDoubleBuffer(16);
    private final ModelRenderer clock,face,hand;
    public RenderPrismarineColossus() {
        ModelBase b=new ModelBase() { };b.textureWidth=256;b.textureHeight=512;
        clock=new ModelRenderer(b,0,0);clock.addBox(-24,-24,-4,48,48,8);
        face=new ModelRenderer(b,0,0);face.addBox(-20,-20,-5,40,40,2);
        hand=new ModelRenderer(b,0,0);hand.addBox(-1,0,-6,2,16,2);
    }
    @Override protected ResourceLocation getEntityTexture(Entity e) { return SKIN; }
    @Override public void doRender(Entity e,double x,double y,double z,float yaw,float partial) {
        if(!(e instanceof EntityPrismarineColossus)) return;
        EntityPrismarineColossus boss=(EntityPrismarineColossus)e;
        if(!boss.dormant() && boss.isEntityAlive()) BossStatus.setBossStatus(boss,true);
        boss.evaluate(pose,partial);
        float brightnessX=OpenGlHelper.lastBrightnessX,brightnessY=OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        GL11.glTranslated(x,y,z);
        GL11.glRotatef(180-(boss.prevRotationYaw+MathHelper.wrapAngleTo180_float(boss.rotationYaw-boss.prevRotationYaw)*partial),0,1,0);
        GL11.glScalef(boss.scale(),boss.scale(),boss.scale());GL11.glEnable(GL11.GL_NORMALIZE);
        bindTexture(SKIN);
        float corruption=boss.variant()==EntityPrismarineColossus.GLITCHED?(float)(0.5+0.5*Math.sin((boss.ticksExisted+partial)*0.73)):0;
        float breakup=boss.state()==ColossusPose.DEATH?(float)ColossusPose.smooth((boss.stateTime(partial)-(boss.variant()==EntityPrismarineColossus.ADMIN?65:100))/70):0;
        model.render(pose,false,corruption,1,breakup);
        if(boss.state()==ColossusPose.CLOCK) renderClock(boss.stateTime(partial));
        GL11.glDisable(GL11.GL_LIGHTING);OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
        float fade=boss.state()==ColossusPose.DEATH?Math.max(0,1-boss.stateTime(partial)/85F):1;
        if(fade>0 && (boss.state()!=ColossusPose.DEATH || ((int)boss.stateTime(partial)/4)%3!=0)) {
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE);
            model.render(pose,true,corruption,fade*(1+boss.weakFlare(partial)),0);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,brightnessX,brightnessY);
        GL11.glPopMatrix();GL11.glPopAttrib();
    }
    private void renderClock(float time) {
        GL11.glPushMatrix();matrix.clear();matrix.put(pose.bones[ColossusPose.LEFT_HAND].matrix);matrix.flip();GL11.glMultMatrix(matrix);
        GL11.glTranslated(0,ColossusPose.FIST_Y,-2.15);GL11.glColor3f(0.85F,0.6F,0.16F);clock.render(0.125F);
        GL11.glColor3f(0.85F,0.90F,0.72F);face.render(0.125F);
        GL11.glColor3f(0.09F,0.18F,0.22F);
        for(int i=0;i<12;i++) { GL11.glPushMatrix();GL11.glRotatef(i*30,0,0,1);GL11.glTranslatef(0,2,0);GL11.glScalef(1,0.2F,1);hand.render(0.125F);GL11.glPopMatrix(); }
        GL11.glPushMatrix();GL11.glRotatef(time*9,0,0,1);hand.render(0.125F);GL11.glPopMatrix();
        GL11.glPushMatrix();GL11.glRotatef(time,0,0,1);GL11.glScalef(1,0.65F,1);hand.render(0.125F);GL11.glPopMatrix();
        GL11.glPopMatrix();
    }
}
