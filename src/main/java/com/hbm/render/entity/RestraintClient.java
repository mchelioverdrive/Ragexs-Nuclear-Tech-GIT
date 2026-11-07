package com.hbm.render.entity;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.hbm.blocks.machine.BlockRestraintDevice.Kind;
import com.hbm.handler.restraint.RestraintState;
import com.hbm.tileentity.machine.TileEntityRestraintDevice;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import org.lwjgl.opengl.GL11;

/** Cosmetic transforms only; authoritative position, AI and damage remain on the server. */
public final class RestraintClient {
    private static final Field MAIN_MODEL = ReflectionHelper.findField(RendererLivingEntity.class, "mainModel", "field_77045_g");
    private static final ResourceLocation TEXTURE = new ResourceLocation("hbm:textures/models/restraint/garments.png");
    private final Deque<Frame> frames = new ArrayDeque<Frame>();
    private final Map<Integer, EntityLivingBase> anchored = new HashMap<Integer, EntityLivingBase>();
    private final ModelRenderer cuffs, jacket, sleeves, seatedLegs;
    public RestraintClient() {
        ModelBase base = new ModelBase() { };
        base.textureWidth = base.textureHeight = 64;
        cuffs = new ModelRenderer(base); jacket = new ModelRenderer(base); sleeves = new ModelRenderer(base); seatedLegs = new ModelRenderer(base);
        // Two open steel loops joined by a short chain.
        for(int x : new int[] {-4, 1}) {
            add(base, cuffs, 0, 32, x, 13, 5, 3, 1, 3);
            add(base, cuffs, 0, 32, x, 16, 5, 3, 1, 3);
            add(base, cuffs, 0, 32, x, 14, 5, 1, 2, 3);
            add(base, cuffs, 0, 32, x + 2, 14, 5, 1, 2, 3);
        }
        add(base, cuffs, 0, 32, -1, 14, 6, 2, 1, 1);
        add(base, jacket, 0, 0, -4.5F, 12, -2.5F, 9, 12, 5);
        for(int y : new int[] {14, 18, 22}) add(base, jacket, 32, 0, -4.6F, y, -2.7F, 10, 1, 6);
        add(base, sleeves, 0, 0, -6, 16, 2, 12, 4, 4);
        add(base, sleeves, 0, 0, -5, 12, 3, 10, 4, 4);
        for(int x : new int[] {-4, 1}) {
            add(base, seatedLegs, 32, 0, x, 8, -1, 3, 3, 8);
            add(base, seatedLegs, 32, 0, x, 1, 4, 3, 7, 3);
            add(base, seatedLegs, 32, 0, x, 0, 3, 3, 2, 5);
        }
    }
    private static void add(ModelBase base, ModelRenderer parent, int u, int v, float x, float y, float z, int w, int h, int d) {
        ModelRenderer p = new ModelRenderer(base, u, v); p.addBox(x, y, z, w, h, d); parent.addChild(p);
    }
    private static final class Frame {
        EntityLivingBase entity;
        ModelBiped model;
        ModelVillager villager;
        boolean leftArm, rightArm, leftLeg, rightLeg, chair;
        float limb;
        ItemStack held;
    }
    private static boolean rigid(RestraintState state) {
        return state.anchored && state.device;
    }
    @SubscribeEvent public void entityTick(LivingUpdateEvent event) {
        if(!event.entity.worldObj.isRemote) return;
        EntityLivingBase entity = event.entityLiving;
        if(rigid(RestraintState.get(entity))) anchored.put(entity.getEntityId(), entity);
        else anchored.remove(entity.getEntityId());
    }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        for(Iterator<EntityLivingBase> it = anchored.values().iterator(); it.hasNext();) {
            EntityLivingBase entity = it.next(); RestraintState state = RestraintState.get(entity);
            if(entity.worldObj != Minecraft.getMinecraft().theWorld || entity.isDead || !rigid(state)) { it.remove(); continue; }
            if(state.dimension == entity.worldObj.provider.dimensionId && entity.worldObj.blockExists(state.x, state.y, state.z)) {
                TileEntity tile = entity.worldObj.getTileEntity(state.x, state.y, state.z);
                if(tile instanceof TileEntityRestraintDevice && ((TileEntityRestraintDevice) tile).kind() == Kind.HANGING) {
                    TileEntityRestraintDevice device = (TileEntityRestraintDevice) tile;
                    net.minecraft.util.Vec3 p = device.hangingPosition(device.animationCycle(0));
                    state.anchorX = p.xCoord; state.anchorY = p.yCoord; state.anchorZ = p.zCoord;
                }
            }
            // Match the server anchor after local gravity, without changing the authoritative hitbox or AI.
            entity.setPosition(state.anchorX, state.anchorY, state.anchorZ);
            entity.prevPosX = entity.lastTickPosX = entity.posX;
            entity.prevPosY = entity.lastTickPosY = entity.posY;
            entity.prevPosZ = entity.lastTickPosZ = entity.posZ;
            entity.motionX = entity.motionY = entity.motionZ = 0;
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void before(RenderLivingEvent.Pre event) throws IllegalAccessException {
        RestraintState state = RestraintState.get(event.entity);
        if(!state.bound()) return;
        ModelBase model = (ModelBase) MAIN_MODEL.get(event.renderer);
        // Arbitrary non-biped renderers retain their native model; no unsafe limb assumptions.
        if(!(model instanceof ModelBiped) && !(model instanceof ModelVillager)) return;
        Frame frame = new Frame(); frame.entity = event.entity; frame.limb = event.entity.limbSwingAmount;
        if(state.cuffs || state.jacket || state.device || state.asleep > 0) {
            frame.held = event.entity.getHeldItem();
            if(frame.held != null) event.entity.setCurrentItemOrArmor(0, null);
        }
        TileEntityRestraintDevice device = null;
        if(state.occupied()) {
            TileEntity tile = event.entity.worldObj.getTileEntity(state.x, state.y, state.z);
            if(tile instanceof TileEntityRestraintDevice) device = (TileEntityRestraintDevice) tile;
        }
        frame.chair = device != null && device.kind() == Kind.CHAIR;
        GL11.glPushMatrix();
        if(device != null && device.kind() == Kind.TABLE || state.asleep > 0 && device == null) {
            GL11.glTranslated(event.x, event.y + (device == null ? 0.3 : 0.38), event.z);
            GL11.glRotatef(device == null ? -event.entity.renderYawOffset : device.yaw(), 0, 1, 0);
            GL11.glRotatef(-90, 1, 0, 0);
            GL11.glTranslated(0, -event.entity.height / 2, 0);
            GL11.glRotatef(device == null ? event.entity.renderYawOffset : -device.yaw(), 0, 1, 0);
            GL11.glTranslated(-event.x, -event.y, -event.z);
        }
        if(model instanceof ModelBiped) {
            frame.model = (ModelBiped) model;
            frame.leftArm = frame.model.bipedLeftArm.showModel; frame.rightArm = frame.model.bipedRightArm.showModel;
            frame.leftLeg = frame.model.bipedLeftLeg.showModel; frame.rightLeg = frame.model.bipedRightLeg.showModel;
            if(state.jacket || state.cuffs || state.device) frame.model.bipedLeftArm.showModel = frame.model.bipedRightArm.showModel = false;
            if(frame.chair) frame.model.bipedLeftLeg.showModel = frame.model.bipedRightLeg.showModel = false;
        } else if(model instanceof ModelVillager) {
            frame.villager = (ModelVillager) model;
            frame.leftArm = frame.villager.villagerArms.showModel;
            frame.leftLeg = frame.villager.leftVillagerLeg.showModel; frame.rightLeg = frame.villager.rightVillagerLeg.showModel;
            if(state.jacket) frame.villager.villagerArms.showModel = false;
            if(frame.chair) frame.villager.leftVillagerLeg.showModel = frame.villager.rightVillagerLeg.showModel = false;
        }
        if(state.device || state.asleep > 0) event.entity.limbSwingAmount = 0;
        frames.push(frame);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void after(RenderLivingEvent.Post event) {
        if(frames.isEmpty() || frames.peek().entity != event.entity) return;
        Frame frame = frames.pop(); RestraintState s = RestraintState.get(event.entity);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(event.x, event.y, event.z); GL11.glRotatef(-event.entity.renderYawOffset, 0, 1, 0);
        float scale = event.entity.height / 1.8F;
        GL11.glScalef(scale, scale, scale);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
        GL11.glColor4f(1, 1, 1, 1);
        if(s.jacket) jacket.render(0.0625F);
        if(frame.model != null && (s.cuffs || s.jacket || s.device) || frame.villager != null && s.jacket) sleeves.render(0.0625F);
        if(s.cuffs) cuffs.render(0.0625F);
        if(frame.chair) seatedLegs.render(0.0625F);
        GL11.glPopMatrix(); GL11.glPopAttrib();
        if(frame.model != null) {
            frame.model.bipedLeftArm.showModel = frame.leftArm; frame.model.bipedRightArm.showModel = frame.rightArm;
            frame.model.bipedLeftLeg.showModel = frame.leftLeg; frame.model.bipedRightLeg.showModel = frame.rightLeg;
        }
        if(frame.villager != null) {
            frame.villager.villagerArms.showModel = frame.leftArm;
            frame.villager.leftVillagerLeg.showModel = frame.leftLeg; frame.villager.rightVillagerLeg.showModel = frame.rightLeg;
        }
        event.entity.limbSwingAmount = frame.limb;
        if(frame.held != null) event.entity.setCurrentItemOrArmor(0, frame.held);
        GL11.glPopMatrix();
    }
}
