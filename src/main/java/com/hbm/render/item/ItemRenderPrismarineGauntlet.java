package com.hbm.render.item;

import com.hbm.render.entity.mob.RenderPrismarineColossus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

public final class ItemRenderPrismarineGauntlet implements IItemRenderer {
    private final ModelRenderer shell,trim,rune;
    public ItemRenderPrismarineGauntlet() {
        ModelBase b=new ModelBase() { }; b.textureWidth=b.textureHeight=256;
        shell=new ModelRenderer(b,0,0);shell.addBox(-3,-1,-3,6,5,6);shell.addBox(-2,-5,-2,4,4,4);
        trim=new ModelRenderer(b,0,0);trim.addBox(-3,-2,-3,6,1,6);trim.addBox(-2.5F,-4,-2.5F,5,1,5);
        for(int i=0;i<3;i++) trim.addBox(-2+i*2,3,-3.5F,1,1,1);
        rune=new ModelRenderer(b,0,0);rune.addBox(-1,0,-3.2F,2,2,1);
    }
    @Override public boolean handleRenderType(ItemStack stack,ItemRenderType type) { return type!=ItemRenderType.INVENTORY; }
    @Override public boolean shouldUseRenderHelper(ItemRenderType type,ItemStack stack,ItemRendererHelper helper) { return true; }
    @Override public void renderItem(ItemRenderType type,ItemStack item,Object... data) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        if(type==ItemRenderType.EQUIPPED_FIRST_PERSON) { GL11.glTranslatef(0.65F,0.25F,0.15F);GL11.glRotatef(-25,0,0,1); }
        else if(type==ItemRenderType.EQUIPPED) { GL11.glTranslatef(0.6F,0.4F,0);GL11.glRotatef(-65,0,0,1); }
        GL11.glScalef(1.8F,1.8F,1.8F);GL11.glEnable(GL11.GL_NORMALIZE);
        Minecraft.getMinecraft().getTextureManager().bindTexture(RenderPrismarineColossus.SKIN);
        GL11.glColor3f(0.23F,0.58F,0.52F);shell.render(0.0625F);
        GL11.glColor3f(0.65F,0.77F,0.6F);trim.render(0.0625F);
        GL11.glColor3f(0.4F,1,0.8F);rune.render(0.0625F);
        GL11.glPopMatrix();GL11.glPopAttrib();
    }
}
