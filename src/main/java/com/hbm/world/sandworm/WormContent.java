package com.hbm.world.sandworm;

import java.util.Random;
import com.hbm.config.SandwormConfig;
import com.hbm.entity.mob.sandworm.EntityTick;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

public final class WormContent {
    private WormContent() { }
    public static Block flesh, guts, tumor, blood, acid, saliva, gas, exit, bloodSplash, acidSplash;
    public static Item bait;
    public static void register() {
        flesh = solid("worm_flesh");
        guts = solid("worm_guts");
        tumor = new Block(Material.clay) {
            @Override public boolean isOpaqueCube() { return false; }
            @Override public boolean renderAsNormalBlock() { return false; }
            @Override public int onBlockPlaced(World w, int x, int y, int z, int side, float hx, float hy, float hz, int meta) { return side; }
            @Override public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity e) {
                if(!w.isRemote && e instanceof EntityPlayer && !((EntityPlayer)e).capabilities.isCreativeMode) {
                    w.setBlock(x, y, z, acidSplash, 0, 3);
                }
            }
        }.setHardness(10).setResistance(30);
        tumor.setBlockBounds(0.0625F, 0, 0.0625F, 0.9375F, 0.9375F, 0.9375F);
        register(tumor, "worm_tumor");
        blood = fluid("worm_blood", 0, false);
        acid = fluid("worm_acid", 1, false);
        saliva = fluid("worm_saliva", 2, false);
        gas = fluid("worm_gas", 3, false);
        bloodSplash = fluid("worm_blood_splash", 0, true);
        acidSplash = fluid("worm_acid_splash", 1, true);
        exit = new Block(Material.rock) {
            @Override public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity e) {
                if(!w.isRemote && e instanceof EntityPlayer && e.ticksExisted % 10 == 0)
                    WormEncounters.get(w).requestExit((EntityPlayer)e, x, z);
            }
        }.setBlockUnbreakable().setResistance(6000000).setLightLevel(0.8F);
        // Like soul sand, a shallow inset lets standing feet intersect this block's collision callback.
        exit.setBlockBounds(0, 0, 0, 1, 0.875F, 1);
        register(exit, "worm_exit");
        bait = new Item() {
            @Override public ItemStack onItemRightClick(ItemStack stack, World w, EntityPlayer p) {
                if(!w.isRemote) {
                    EntityItem drop = new EntityItem(w, p.posX, p.posY + p.getEyeHeight(), p.posZ, new ItemStack(this));
                    drop.motionX = p.getLookVec().xCoord * 0.7;
                    drop.motionY = p.getLookVec().yCoord * 0.7 + 0.2;
                    drop.motionZ = p.getLookVec().zCoord * 0.7;
                    drop.delayBeforeCanPickup = 40;
                    if(w.spawnEntityInWorld(drop) && !p.capabilities.isCreativeMode) stack.stackSize--;
                }
                return stack;
            }
        }.setUnlocalizedName("worm_bait").setTextureName("hbm:worm_bait").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerItem(bait, "worm_bait");
        GameRegistry.addShapelessRecipe(new ItemStack(bait, 2), Items.rotten_flesh, Items.slime_ball);
    }
    private static Block solid(String name) {
        Block b = new Block(Material.clay) { };
        b.setBlockUnbreakable().setResistance(6000000).setStepSound(Block.soundTypeGravel);
        register(b, name); return b;
    }
    private static void register(Block block, String name) {
        block.setBlockName(name).setBlockTextureName("hbm:" + name).setCreativeTab(CreativeTabs.tabBlock);
        GameRegistry.registerBlock(block, name);
    }
    private static Block fluid(String name, int kind, boolean temporary) {
        Fluid f = new Fluid("rnt_" + name).setDensity(kind == 3 ? -100 : 1100).setViscosity(1800).setGaseous(kind == 3);
        if(!FluidRegistry.registerFluid(f)) throw new IllegalStateException("Duplicate Sandworm fluid " + name);
        Block b = new WormFluid(f, kind, temporary);
        register(b, name); return b;
    }
    private static final class WormFluid extends BlockFluidClassic {
        private final int kind;
        private final boolean temporary;
        WormFluid(Fluid f, int kind, boolean temporary) {
            super(f, Material.water);
            this.kind = kind; this.temporary = temporary;
            setQuantaPerBlock(4);
            setLightOpacity(0);
            if(kind == 1 || kind == 2) setLightLevel(kind == 1 ? 1F : 7F / 15F);
        }
        @Override public int getRenderColor(int meta) { return kind == 0 ? 0xB32939 : kind == 1 ? 0xB8D742 : kind == 2 ? 0xE0DBB4 : 0xA2AD62; }
        @Override @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
        public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister icons) {
            super.registerBlockIcons(icons); getFluid().setIcons(blockIcon);
        }
        @Override public void onBlockAdded(World w, int x, int y, int z) {
            if(temporary) w.scheduleBlockUpdate(x, y, z, this, 100);
            else super.onBlockAdded(w, x, y, z);
        }
        @Override public void onNeighborBlockChange(World w, int x, int y, int z, Block neighbor) {
            if(!temporary) super.onNeighborBlockChange(w, x, y, z, neighbor);
        }
        @Override public void updateTick(World w, int x, int y, int z, Random random) {
            if(!w.isRemote && w.provider.dimensionId == SandwormConfig.dimension && WormEncounters.get(w).retiringAt(x, z)) {
                // Retired fluids must not flow into the void as cleanup removes their supporting floor.
                w.setBlock(x, y, z, net.minecraft.init.Blocks.air, 0, 2); return;
            }
            if(temporary) { w.setBlockToAir(x, y, z); return; }
            super.updateTick(w, x, y, z, random);
        }
        @Override public void onEntityCollidedWithBlock(World w, int x, int y, int z, Entity e) {
            if(w.isRemote) return;
            if(kind == 0 && e instanceof EntityTick) ((EntityTick)e).growFromBlood();
            if(e instanceof EntityPlayer) {
                if(kind < 2) { e.motionX *= 0.4; e.motionZ *= 0.4; }
                if(e.ticksExisted % 20 == 0 && kind != 2) e.attackEntityFrom(DamageSource.generic,
                        kind == 0 ? SandwormConfig.bloodDamage : kind == 1 ? SandwormConfig.acidDamage : SandwormConfig.gasDamage);
            }
        }
    }
}
