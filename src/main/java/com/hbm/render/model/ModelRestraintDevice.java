package com.hbm.render.model;

import com.hbm.blocks.machine.BlockRestraintDevice.Kind;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

/** Native 1.7.10 textured geometry. Moving components have independent pivots. */
public final class ModelRestraintDevice extends ModelBase {
    private final ModelRenderer frame, blade, hatch, straps, injector;
    private final Kind kind;

    public ModelRestraintDevice(Kind kind) {
        this.kind = kind; textureWidth = textureHeight = 64;
        frame = new ModelRenderer(this); blade = new ModelRenderer(this);
        hatch = new ModelRenderer(this); straps = new ModelRenderer(this); injector = new ModelRenderer(this);
        switch(kind) {
            case GUILLOTINE: guillotine(); break;
            case BULL: bull(); break;
            case CHAIR: chair(); break;
            case TABLE: table(); break;
        }
    }

    private void box(ModelRenderer parent, int u, int v, float x, float y, float z, int w, int h, int d) {
        ModelRenderer part = new ModelRenderer(this, u, v);
        part.addBox(x, y, z, w, h, d);
        parent.addChild(part);
    }
    private void timber(float x, float y, float z, int w, int h, int d) { box(frame, 0, 0, x, y, z, w, h, d); }
    private void metal(float x, float y, float z, int w, int h, int d) { box(frame, 0, 32, x, y, z, w, h, d); }

    private void guillotine() {
        timber(-22, 0, -21, 44, 3, 42);
        timber(-18, 3, -3, 4, 55, 6); timber(14, 3, -3, 4, 55, 6);
        timber(-18, 56, -3, 36, 4, 6);
        timber(-12, 9, -18, 24, 3, 38);
        timber(-12, 12, -17, 3, 12, 3); timber(9, 12, -17, 3, 12, 3);
        // Split neck stock leaves an actual opening rather than a painted-on hole.
        timber(-14, 24, -3, 10, 5, 6); timber(4, 24, -3, 10, 5, 6);
        timber(-5, 29, -3, 10, 3, 6);
        metal(-14, 30, -2, 1, 25, 2); metal(13, 30, -2, 1, 25, 2);
        for(int x : new int[] {-17, 15}) for(int y : new int[] {5, 25, 52}) metal(x, y, -4, 2, 2, 1);
        box(blade, 0, 32, -13, 0, -1, 26, 7, 2);
        for(int i = 0; i < 6; ++i) box(blade, 0, 32, -13 + i * 4, -i, -1, 5, 2 + i, 2);
        metal(19, 18, -1, 2, 14, 2); metal(17, 31, -2, 6, 2, 4);
        box(straps, 32, 0, -9, 12, 5, 18, 2, 3);
        box(straps, 32, 0, -9, 12, 15, 18, 2, 3);
    }
    private void bull() {
        // Eight wall strips form an open chamber. The roof and belly are separate from the hatch.
        metal(-20, 7, -21, 40, 3, 42);
        metal(-20, 10, -21, 3, 34, 42); metal(17, 10, -21, 3, 34, 42);
        metal(-17, 44, -21, 34, 4, 42);
        metal(-17, 9, -23, 34, 35, 2);
        metal(-17, 44, -23, 34, 2, 2);
        // Rear opening, with frame and pivoting metal door.
        metal(-17, 10, 21, 4, 34, 3); metal(13, 10, 21, 4, 34, 3);
        metal(-13, 41, 21, 26, 3, 3);
        hatch.setRotationPoint(-13, 10, 23);
        box(hatch, 0, 32, 0, 0, -1, 26, 31, 2);
        box(hatch, 32, 0, 19, 9, 1, 4, 3, 2);
        for(int x : new int[] {-17, 11}) for(int z : new int[] {-17, 11}) {
            metal(x, 0, z, 6, 10, 6); box(frame, 32, 0, x - 1, 0, z - 1, 8, 3, 8);
        }
        metal(-12, 33, -31, 24, 15, 13); metal(-10, 29, -37, 20, 10, 9);
        metal(-10, 45, -28, 4, 9, 4); metal(6, 45, -28, 4, 9, 4);
        metal(-14, 52, -28, 8, 3, 4); metal(6, 52, -28, 8, 3, 4);
        box(frame, 32, 0, -13, 40, -32, 3, 3, 3); box(frame, 32, 0, 10, 40, -32, 3, 3, 3);
        metal(-24, 41, -25, 7, 2, 7); metal(17, 41, -25, 7, 2, 7);
        metal(-2, 33, 24, 3, 13, 3); metal(-2, 30, 26, 3, 5, 5);
        for(int z = -18; z <= 18; z += 9) { metal(-22, 14, z, 2, 16, 2); metal(20, 14, z, 2, 16, 2); }
        // Fuel grate and chimney use the existing furnace fuel inventory.
        box(frame, 32, 0, -10, 2, -14, 20, 3, 28);
        for(int x = -8; x <= 8; x += 4) metal(x, 4, -13, 1, 2, 26);
        metal(12, 48, -8, 4, 7, 4);
    }
    private void chair() {
        timber(-7, 8, -7, 14, 3, 14); timber(-7, 10, -7, 14, 23, 3);
        for(int x : new int[] {-7, 4}) for(int z : new int[] {-6, 4}) timber(x, 0, z, 3, 9, 3);
        timber(-8, 13, -6, 3, 3, 15); timber(5, 13, -6, 3, 3, 15);
        metal(-8, 8, 5, 2, 7, 2); metal(6, 8, 5, 2, 7, 2);
        metal(-4, 0, 8, 8, 2, 5);
        box(frame, 0, 32, -4, 32, -4, 8, 3, 8);
        metal(-2, 35, -2, 4, 3, 4);
        box(straps, 32, 0, -8, 15, 1, 4, 2, 3); box(straps, 32, 0, 4, 15, 1, 4, 2, 3);
        box(straps, 32, 0, -6, 10, 2, 12, 2, 3);
        box(frame, 32, 0, 4, 20, -9, 4, 7, 3);
        for(int y = 3; y < 34; y += 3) box(frame, 32, 0, 6, y, -9, 1, 2, 1);
        metal(8, 5, -5, 3, 7, 6);
    }
    private void table() {
        metal(-11, 11, -22, 22, 3, 44);
        box(frame, 0, 0, -10, 14, -21, 20, 2, 42);
        for(int x : new int[] {-9, 6}) for(int z : new int[] {-19, 16}) metal(x, 1, z, 3, 11, 3);
        metal(-9, 3, -1, 18, 2, 2);
        box(frame, 0, 0, -7, 16, -20, 14, 2, 8);
        metal(12, 0, -17, 2, 34, 2); metal(3, 33, -17, 11, 2, 2);
        box(frame, 0, 32, 4, 27, -17, 4, 6, 3);
        box(injector, 0, 32, 11, 0, -3, 7, 2, 2);
        box(injector, 32, 0, 8, 0.5F, -2.5F, 4, 1, 1);
        for(int z : new int[] {-10, 2, 15}) box(straps, 32, 0, -11, 0, z, 22, 2, 3);
        metal(-14, 12, -8, 3, 2, 16); metal(11, 12, -8, 3, 2, 16);
    }

    public void render(float cycle, float hatchAmount, float restraintAmount, boolean active) {
        frame.render(0.0625F);
        blade.rotationPointY = 47 - (active ? cycle < 5 ? cycle / 5F * 19 : cycle < 12 ? 19 : Math.max(0, (45 - cycle) / 33F * 19) : 0);
        hatch.rotateAngleY = hatchAmount * -1.65F;
        straps.rotationPointY = kind == Kind.TABLE ? 30 - restraintAmount * 6 : (1 - restraintAmount) * 4;
        injector.rotationPointY = 19;
        injector.rotationPointX = active ? (float) -Math.sin(Math.min(1, cycle / 30F) * Math.PI) * 4 : 0;
        if(kind == Kind.GUILLOTINE) blade.render(0.0625F);
        if(kind == Kind.BULL) hatch.render(0.0625F);
        if(kind == Kind.TABLE) injector.render(0.0625F);
        straps.render(0.0625F);
    }
}
