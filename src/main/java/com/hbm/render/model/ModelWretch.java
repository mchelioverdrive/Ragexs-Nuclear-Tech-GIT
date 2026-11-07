package com.hbm.render.model;

import com.hbm.entity.mob.gemini.EntityWretch;
import com.hbm.entity.mob.gemini.WretchPose;
import com.hbm.entity.mob.gemini.WretchAnatomy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.util.Vec3;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Mature organism. Every attachment is in block units relative to the shared skeleton. */
public final class ModelWretch extends ModelBase {
    private static final float UNIT = 1F / 16F;
    private static final ResourceLocation DETAIL_TEXTURE = new ResourceLocation("hbm:textures/blocks/concrete_white.png");
    private final ModelRenderer flesh, hide, ribs, tendons, skull, jaw, sockets, teeth;
    private final ModelRenderer bone, joint, palm, foot;
    private final ModelRenderer[][][] fingers = new ModelRenderer[2][5][3];
    private final ModelRenderer[] pores = new ModelRenderer[27], rims = new ModelRenderer[27];
    private final ModelRenderer[][] roots = new ModelRenderer[17][3];
    private static final int BASE_PEOPLE = 7, PEOPLE = BASE_PEOPLE + WretchAnatomy.LIMIT;
    private final ModelRenderer[] people = new ModelRenderer[PEOPLE], faces = new ModelRenderer[PEOPLE], mouths = new ModelRenderer[PEOPLE], embeddedArms = new ModelRenderer[PEOPLE];
    private final ModelRenderer[] graftFingers = new ModelRenderer[PEOPLE];
    private final Vec3 attach = Vec3.createVectorHelper(0, 0, 0);
    private final Vec3 end = Vec3.createVectorHelper(0, 0, 0);
    private final Vec3 middle = Vec3.createVectorHelper(0, 0, 0);

    public ModelWretch() {
        textureWidth = textureHeight = 16;
        flesh = empty();
        add(flesh, -17, -53, -12, 33, 62, 26, -0.055F, 0.09F);
        add(flesh, -21, 0, -16, 39, 43, 31, 0.12F, -0.08F);
        add(flesh, -19, 36, -12, 34, 28, 25, -0.07F, 0.13F);
        add(flesh, -13, -61, -11, 26, 23, 23, 0, 0.08F);
        add(flesh, -5, 57, 8, 10, 42, 12, 0, -0.04F); // Continuous neck into the skull base.
        add(flesh, -29, 61, -7, 13, 17, 20, 0.08F, -0.12F);
        add(flesh, 19, 51, -7, 15, 18, 19, -0.09F, 0.16F);
        // Irregular fused tissue lobes, rather than a scaled quadruped abdomen.
        for(int i = 0; i < 14; i++) {
            ModelRenderer lobe = box(-7, -10, -6, 12 + i % 4, 18 + i % 5, 12);
            lobe.setRotationPoint((i % 2 == 0 ? -1 : 1) * (12 + i % 6), -44 + i * 7, (i % 3 - 1) * 9);
            lobe.rotateAngleZ = (i % 3 - 1) * 0.21F; lobe.rotateAngleX = i * 0.071F; flesh.addChild(lobe);
        }
        hide = empty(); // Residual shoulder pelt and tiny torn ear: the only bear-like masses.
        add(hide, -24, 42, -17, 23, 22, 22, 0.1F, -0.2F);
        add(hide, 2, 36, -18, 20, 18, 16, -0.1F, 0.17F);
        ribs = empty(); tendons = empty();
        for(int i = 0; i < 9; i++) {
            int side = i % 2 == 0 ? -1 : 1;
            ModelRenderer rib = box(-1, -1, -1, 21 - i % 4, 2, 3);
            rib.setRotationPoint(side < 0 ? -19 : 0, -22 + i * 8, 13 + i % 3);
            rib.rotateAngleZ = side * (0.1F + i * 0.024F); ribs.addChild(rib);
            ModelRenderer tendon = box(-1, -35, -1, 2, 70 + i % 4 * 3, 2);
            tendon.setRotationPoint(-16 + i * 4, 2 + i % 3 * 7, i % 2 == 0 ? 16 : -16);
            tendon.rotateAngleZ = (i % 3 - 1) * 0.08F; tendons.addChild(tendon);
        }
        skull = empty();
        add(skull, -7, -5, -4, 14, 12, 18, 0, -0.04F);
        add(skull, -5, -4, 9, 9, 8, 25, -0.035F, 0);
        add(skull, -3, -3, 29, 6, 6, 13, 0, 0.025F);
        add(skull, -6, 3, 2, 3, 7, 11, 0, 0.22F);
        ModelRenderer ear = box(-2, 0, -1, 3, 5, 2); ear.setRotationPoint(-7, 7, -1); ear.rotateAngleZ = 0.5F;
        skull.addChild(ear);
        jaw = box(-4, -3, 0, 8, 3, 39); jaw.setRotationPoint(0, -4, 0);
        sockets = box(-7.1F, 0, 5, 2, 4, 7); sockets.addBox(5.3F, 0.8F, 6, 2, 3, 6); sockets.addBox(-2, -1, 40, 4, 3, 2);
        teeth = empty();
        for(int i = 0; i < 9; i++) {
            ModelRenderer tooth = box(-0.5F, -4 - i % 3, -0.5F, 1, 4 + i % 3, 2);
            tooth.setRotationPoint(i % 2 == 0 ? -3 : 3, -3, 10 + i * 3); tooth.rotateAngleZ = (i % 3 - 1) * 0.16F; teeth.addChild(tooth);
        }
        bone = box(-0.5F, 0, -0.5F, 1, 16, 1);
        joint = box(-5, -5, -5, 10, 10, 10);
        palm = box(-8, -3, -5, 16, 6, 15);
        foot = box(-6, -4, -9, 12, 7, 26);
        for(int arm = 0; arm < 2; arm++) for(int finger = 0; finger < 5; finger++) {
            float length = (finger == 0 ? 8 : 15 + (finger * 7 + arm * 11) % 17);
            for(int section = 0; section < 3; section++) {
                float partLength = length * (section == 0 ? 0.43F : section == 1 ? 0.34F : 0.23F);
                ModelRenderer phalanx = box(-1.4F, -1.4F, -1, 3, 3, (int) partLength + 2);
                if(section == 0) phalanx.setRotationPoint(-7 + finger * 3.4F, 0, finger == 0 ? -1 : 8);
                else phalanx.setRotationPoint(0, 0, length * (section == 1 ? 0.43F : 0.34F));
                fingers[arm][finger][section] = phalanx;
                if(section > 0) fingers[arm][finger][section - 1].addChild(phalanx);
            }
        }
        // Recessed dark cores, raised tissue lips and root chains all share the same pore origin.
        for(int i = 0; i < pores.length; i++) {
            int size = 5 + i % 5;
            pores[i] = box(-size / 2F, -size / 2F, -0.4F, size, size, 1);
            float angle = i * 2.399963F;
            pores[i].setRotationPoint((float) Math.sin(angle) * (17 + i % 3), -49 + i * 4.1F, (float) Math.cos(angle) * (15 + i % 3));
            pores[i].rotateAngleY = angle; pores[i].rotateAngleZ = (i % 3 - 1) * 0.16F;
            rims[i] = empty();
            rims[i].addBox(-size / 2F - 1, -size / 2F - 1, -0.8F, size + 2, 2, 3);
            rims[i].addBox(-size / 2F - 1, size / 2F - 1, -0.8F, size + 2, 2, 3);
            rims[i].addBox(-size / 2F - 1, -size / 2F, -0.8F, 2, size, 3);
            rims[i].addBox(size / 2F - 1, -size / 2F, -0.8F, 2, size, 3);
            if(i < roots.length) for(int j = 0; j < 3; j++) {
                roots[i][j] = box(-0.7F, -0.7F, -1, j == 0 ? 2 : 1, j == 0 ? 2 : 1, 10 + (i + j) % 8);
                if(j > 0) { roots[i][j].setRotationPoint(0, 0, 9 + (i + j - 1) % 8); roots[i][j - 1].addChild(roots[i][j]); }
            }
        }
        for(int i = 0; i < people.length; i++) {
            people[i] = empty();
            add(people[i], -4, -10, -3, 8, 18, 5, 0, 0.12F); // Flattened fused human chest.
            add(people[i], -5, 3, -2, 10, 4, 5, 0, -0.07F);
            faces[i] = box(-3, 0, -2, 6, 8, 5); faces[i].setRotationPoint(0, 7, 0);
            ModelRenderer nose = box(-1, 2, 2, 2, 3, 2); faces[i].addChild(nose);
            mouths[i] = box(-2, -1, -0.5F, 4, 3, 2); mouths[i].setRotationPoint(0, 1, 2.5F);
            embeddedArms[i] = box(-1.2F, -13, -1.2F, 3, 14, 3); embeddedArms[i].setRotationPoint(i % 2 == 0 ? -4 : 4, 3, 0);
            ModelRenderer forearm = box(-1, -13, -1, 2, 14, 2); forearm.setRotationPoint(0, -12, 0); forearm.rotateAngleZ = i % 2 == 0 ? 0.75F : -0.55F;
            embeddedArms[i].addChild(forearm);
            graftFingers[i] = box(-2, -4, -1, 1, 5, 1); graftFingers[i].addBox(0, -5, -1, 1, 6, 1); graftFingers[i].addBox(2, -3, -1, 1, 4, 1);
            graftFingers[i].setRotationPoint(0, -12, 0); forearm.addChild(graftFingers[i]);
        }
    }
    private ModelRenderer empty() { return new ModelRenderer(this, 0, 0); }
    private ModelRenderer box(float x, float y, float z, int w, int h, int d) { ModelRenderer p = empty(); p.addBox(x, y, z, w, h, d); return p; }
    private void add(ModelRenderer parent, float x, float y, float z, int w, int h, int d, float rx, float rz) {
        ModelRenderer child = box(-w / 2F, -h / 2F, -d / 2F, w, h, d);
        child.setRotationPoint(x + w / 2F, y + h / 2F, z + d / 2F); child.rotateAngleX = rx; child.rotateAngleZ = rz; parent.addChild(child);
    }
    private void color(float r, float g, float b, EntityWretch boss) {
        float hurt = boss.hurtTime > 0 ? 0.7F : 1;
        GL11.glColor4f(r, g * hurt, b * hurt, 1);
    }
    public void renderBody(EntityWretch boss, float partial) {
        WretchPose p = boss.pose(partial);
        GL11.glPushMatrix();
        GL11.glTranslated(p.body.xCoord, p.body.yCoord, p.body.zCoord);
        GL11.glRotatef(p.lean * 57.29578F, 1, 0, 0);
        GL11.glPushMatrix(); GL11.glScalef(1 + p.breath * 0.008F, 1, 1 + p.breath * 0.013F);
        color(0.56F, 0.22F, 0.19F, boss); flesh.render(UNIT);
        // A red source texture cannot be tinted into exposed bone or recognizable human skin.
        Minecraft.getMinecraft().getTextureManager().bindTexture(DETAIL_TEXTURE);
        color(0.23F, 0.14F, 0.10F, boss); hide.render(UNIT);
        color(0.68F, 0.49F, 0.35F, boss); ribs.render(UNIT);
        color(0.62F, 0.36F, 0.28F, boss); tendons.render(UNIT);
        GL11.glPopMatrix();
        for(int i = 0; i < pores.length; i++) {
            GL11.glPushMatrix();
            ModelRenderer hole = pores[i];
            GL11.glTranslatef(hole.rotationPointX * UNIT, hole.rotationPointY * UNIT, hole.rotationPointZ * UNIT);
            GL11.glRotatef(hole.rotateAngleY * 57.29578F, 0, 1, 0); GL11.glRotatef(hole.rotateAngleZ * 57.29578F, 0, 0, 1);
            // Render at the attachment origin, without double-applying the pore's position.
            float px = hole.rotationPointX, py = hole.rotationPointY, pz = hole.rotationPointZ, ry = hole.rotateAngleY, rz = hole.rotateAngleZ;
            hole.setRotationPoint(0, 0, 0); hole.rotateAngleY = hole.rotateAngleZ = 0;
            color(0.065F, 0.018F, 0.014F, boss); hole.render(UNIT);
            hole.setRotationPoint(px, py, pz); hole.rotateAngleY = ry; hole.rotateAngleZ = rz;
            color(0.48F, 0.18F, 0.14F, boss); rims[i].render(UNIT);
            if(i < roots.length) {
                for(int j = 0; j < 3; j++) {
                    roots[i][j].rotateAngleX = (float) (Math.sin(p.age * 0.018 + i * 1.7 - j * 0.8) * 0.11 + (i % 3 - 1) * 0.36);
                    roots[i][j].rotateAngleY = (i % 2 == 0 ? -1 : 1) * (0.19F + j * 0.28F);
                }
                color(0.34F, 0.19F, 0.11F, boss); roots[i][0].render(UNIT);
            }
            GL11.glPopMatrix();
        }
        GL11.glPopMatrix();
        Vec3 h = p.head(0);
        GL11.glPushMatrix(); GL11.glTranslated(h.xCoord, h.yCoord, h.zCoord);
        GL11.glRotatef(-p.headYaw * 57.29578F, 0, 1, 0); GL11.glRotatef(p.headPitch * 57.29578F, 1, 0, 0);
        color(0.73F, 0.60F, 0.43F, boss); skull.render(UNIT);
        color(0.06F, 0.025F, 0.02F, boss); sockets.render(UNIT);
        jaw.rotateAngleX = -0.1F - (float) (Math.sin(p.age * 0.047) + 1) * 0.07F - (boss.state() == EntityWretch.FEED ? 0.15F : 0);
        color(0.50F, 0.29F, 0.22F, boss); jaw.render(UNIT);
        color(0.83F, 0.76F, 0.57F, boss); teeth.render(UNIT); GL11.glPopMatrix();
        for(int limb = 0; limb < 4; limb++) {
            for(int segment = 0; segment < 2; segment++) {
                Vec3 a = p.joint(limb, segment), b = p.joint(limb, segment + 1);
                color(0.57F, 0.33F, 0.25F, boss); renderSegment(a, b, limb < 2 ? 0.43F : 0.49F);
                // Exposed bone and trailing tendon remain attached along the same bone axis.
                middle.xCoord = a.xCoord + 0.12; middle.yCoord = a.yCoord; middle.zCoord = a.zCoord;
                end.xCoord = b.xCoord + 0.12; end.yCoord = b.yCoord; end.zCoord = b.zCoord;
                color(0.76F, 0.64F, 0.46F, boss); renderSegment(middle, end, 0.17F);
                renderJoint(a, boss, limb < 2 ? 0.7F : 0.75F);
            }
            Vec3 tip = p.joint(limb, 2);
            renderJoint(tip, boss, 0.65F);
            GL11.glPushMatrix(); GL11.glTranslated(tip.xCoord, tip.yCoord, tip.zCoord);
            if(limb < 2) {
                GL11.glRotatef(limb == 0 ? -13 : 19, 0, 1, 0);
                color(0.53F, 0.27F, 0.21F, boss); palm.render(UNIT);
                for(int f = 0; f < 5; f++) {
                    for(int j = 0; j < 3; j++) {
                        ModelRenderer phalanx = fingers[limb][f][j];
                        phalanx.rotateAngleX = 0.06F + p.curl * (0.28F + j * 0.17F) + (float) Math.sin(p.age * 0.028 + f * 1.8 + limb * 3 - j) * 0.045F;
                        phalanx.rotateAngleY = j == 0 ? (f - 2) * 0.13F + (limb == 0 && f == 3 ? 0.3F : 0) : (f % 2 == 0 ? -0.1F : 0.12F);
                    }
                    color(0.64F, 0.41F, 0.29F, boss); fingers[limb][f][0].render(UNIT);
                }
            } else {
                GL11.glRotatef(p.footPitch[limb - 2] * 57.29578F, 1, 0, 0);
                GL11.glRotatef(p.footRoll[limb - 2] * 57.29578F, 0, 0, 1);
                color(0.32F, 0.20F, 0.14F, boss); foot.render(UNIT);
            }
            GL11.glPopMatrix();
        }
        renderPeople(boss, p);
        renderAssimilation(boss, p, partial);
    }
    private void renderPeople(EntityWretch boss, WretchPose p) {
        EntityPlayer observer = boss.worldObj.getClosestPlayerToEntity(boss, 24);
        boolean nearby = observer != null;
        int count = BASE_PEOPLE + boss.anatomy.size();
        for(int i = 0; i < count; i++) {
            int seed = i < BASE_PEOPLE ? i * 193 : boss.anatomy.seed(i - BASE_PEOPLE);
            GL11.glPushMatrix();
            if(i == 0) {
                // Glenn-inspired face in a limb, as in Old Bones. Other grafts have no assigned identity.
                Vec3 a = p.joint(0, 0), b = p.joint(0, 1);
                GL11.glTranslated(a.xCoord + (b.xCoord - a.xCoord) * 0.72, a.yCoord + (b.yCoord - a.yCoord) * 0.72, a.zCoord + (b.zCoord - a.zCoord) * 0.72 + 0.22);
                GL11.glRotatef(-17, 0, 0, 1);
            } else if(i >= BASE_PEOPLE) {
                p.remainLocal(attach, seed);
                GL11.glTranslated(attach.xCoord, attach.yCoord, attach.zCoord);
                GL11.glRotatef((float) ((seed % 1009) * (360D / 1009)), 0, 1, 0);
                GL11.glRotatef((seed % 61) - 30, 0, 0, 1);
                // A fixed anatomical attachment, compressed into a surrounding lobe of host tissue.
                color(0.48F, 0.21F, 0.18F, boss);
                GL11.glPushMatrix(); GL11.glScalef(1.1F, 1.5F, 0.65F); joint.render(UNIT); GL11.glPopMatrix();
                float size = 0.72F + (seed % 23) * 0.02F;
                GL11.glScalef(size, 0.8F + ((seed >>> 6) % 19) * 0.025F, 0.65F + ((seed >>> 12) % 17) * 0.025F);
            } else {
                p.bodyPoint(attach, (i % 2 == 0 ? -1 : 1) * (0.85 + i % 3 * 0.15), -3 + i * 1.13, i % 3 == 0 ? -0.9 : 1.05);
                GL11.glTranslated(attach.xCoord, attach.yCoord, attach.zCoord);
                GL11.glRotatef(i % 3 == 0 ? 165 : (i % 2 == 0 ? -32 : 27), 0, 1, 0);
                GL11.glRotatef((i % 2 == 0 ? -1 : 1) * (12 + i * 7), 0, 0, 1);
            }
            // Only one graft reacts at once; long gaps and different periods prevent a synchronized chorus.
            double clock = p.age + seed * 0.013 + boss.getEntityId() * 19L;
            boolean selected = ((long) (p.age + boss.getEntityId() * 19L) / 137L) % count == i;
            float reaction = nearby && selected ? (float) Math.pow(Math.max(0, Math.sin(clock * 0.029 + i)), 4) : 0;
            double watch = nearby ? MathHelper.wrapAngleTo180_double(Math.toDegrees(Math.atan2(-(observer.posX - boss.posX), observer.posZ - boss.posZ)) - boss.rotationYaw) : 0;
            faces[i].rotateAngleY = reaction * (float) MathHelper.clamp_double(watch * Math.PI / 180, -0.32, 0.32);
            faces[i].rotateAngleZ = reaction * (float) Math.sin(clock * (0.011 + i * 0.0013) + i * 3) * 0.035F;
            embeddedArms[i].rotateAngleZ = (i % 2 == 0 ? -0.45F : 0.37F) + reaction * 0.17F;
            graftFingers[i].rotateAngleX = reaction * 0.42F;
            color(i == 0 ? 0.66F : 0.50F + (seed % 9) * 0.015F, 0.32F + (seed % 7) * 0.01F, 0.27F, boss);
            if(i < BASE_PEOPLE || seed % 4 != 2) people[i].render(UNIT);
            if(i < BASE_PEOPLE || seed % 4 != 3) faces[i].render(UNIT);
            embeddedArms[i].render(UNIT);
            GL11.glPushMatrix(); GL11.glTranslatef(0, 7 * UNIT, 0);
            GL11.glRotatef(faces[i].rotateAngleY * 57.29578F, 0, 1, 0); GL11.glRotatef(faces[i].rotateAngleZ * 57.29578F, 0, 0, 1);
            color(0.065F, 0.025F, 0.02F, boss);
            if(i < BASE_PEOPLE || seed % 4 != 3) {
            GL11.glPushMatrix(); GL11.glTranslatef(-0.11F, 0.31F, 0.19F); GL11.glScalef(0.075F, 0.045F, 0.03F); joint.render(UNIT); GL11.glPopMatrix();
            GL11.glPushMatrix(); GL11.glTranslatef(0.11F, 0.3F, 0.19F); GL11.glScalef(0.075F, 0.045F, 0.03F); joint.render(UNIT); GL11.glPopMatrix();
            }
            // Tiny pupil shifts suggest attention without implying speech or control of the organism.
            if(i < BASE_PEOPLE || seed % 4 != 3) {
                color(0.65F, 0.57F, 0.43F, boss);
                for(int eye = 0; eye < 2; eye++) {
                    GL11.glPushMatrix(); GL11.glTranslatef((eye == 0 ? -0.11F : 0.11F) + faces[i].rotateAngleY * 0.05F, 0.305F, 0.215F);
                    GL11.glScalef(0.019F, 0.025F, 0.012F); joint.render(UNIT); GL11.glPopMatrix();
                }
            }
            color(0.065F, 0.025F, 0.02F, boss);
            mouths[i].rotateAngleX = reaction * 0.35F;
            GL11.glScalef(1, 0.24F + reaction * 0.7F, 1); if(i < BASE_PEOPLE || seed % 4 != 3) mouths[i].render(UNIT);
            GL11.glPopMatrix(); GL11.glPopMatrix();
        }
    }
    private void renderAssimilation(EntityWretch boss, WretchPose p, float partial) {
        if(boss.state() != EntityWretch.ASSIMILATE && boss.state() != EntityWretch.PULL) return;
        double growth = boss.state() == EntityWretch.ASSIMILATE ? MathHelper.clamp_double(boss.stateTime(partial) / 100D, 0, 1) : 0;
        Vec3 hand = p.joint(boss.attackArm(), 2);
        // Host-attached strands progressively encircle the actual live victim. No render-only entity.
        for(int i = 0; i < 8; i++) {
            double angle = i * 2.399963 + p.age * 0.012, radius = 0.45 + (1 - growth) * 0.3;
            p.bodyPoint(attach, (boss.attackArm() == 0 ? -1 : 1) * 0.8, -1.3 + (i % 3 - 1) * .3, 1.1);
            end.xCoord = hand.xCoord + Math.cos(angle) * radius; end.yCoord = hand.yCoord + (i % 3 - 1) * .65; end.zCoord = hand.zCoord + .45 + Math.sin(angle) * radius;
            middle.xCoord = attach.xCoord + (end.xCoord - attach.xCoord) * (.3 + growth * .7);
            middle.yCoord = attach.yCoord + (end.yCoord - attach.yCoord) * (.3 + growth * .7);
            middle.zCoord = attach.zCoord + (end.zCoord - attach.zCoord) * (.3 + growth * .7);
            color(.46F + i % 3 * .04F, .21F, .16F, boss); renderSegment(attach, middle, .07F + (float) growth * .1F);
            if(growth > 0.2) renderJoint(middle, boss, .18F + (float) growth * .38F);
        }
    }
    private void renderJoint(Vec3 at, EntityWretch boss, float size) {
        GL11.glPushMatrix(); GL11.glTranslated(at.xCoord, at.yCoord, at.zCoord); GL11.glScalef(size, size, size);
        color(0.56F, 0.29F, 0.22F, boss); joint.render(UNIT); GL11.glPopMatrix();
    }
    private void renderSegment(Vec3 a, Vec3 b, float width) {
        double dx = b.xCoord - a.xCoord, dy = b.yCoord - a.yCoord, dz = b.zCoord - a.zCoord;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        GL11.glPushMatrix(); GL11.glTranslated(a.xCoord, a.yCoord, a.zCoord);
        GL11.glRotated(Math.toDegrees(Math.atan2(dx, dz)), 0, 1, 0);
        GL11.glRotated(Math.toDegrees(Math.atan2(Math.sqrt(dx * dx + dz * dz), dy)), 1, 0, 0);
        GL11.glScalef(width * 16, (float) length, width * 16); bone.render(UNIT); GL11.glPopMatrix();
    }
}
