package com.hbm.entity.mob.colossus;

import com.hbm.config.ColossusConfig;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

public final class ColossusContent {
    private ColossusContent() { }
    public static Item gauntlet,shard,egg;
    public static void register() {
        gauntlet=new ItemSword(Item.ToolMaterial.EMERALD) {
            @Override public boolean isFull3D() { return true; }
            @Override public void addInformation(ItemStack stack,EntityPlayer player,java.util.List lines,boolean advanced) {
                lines.add(net.minecraft.util.StatCollector.translateToLocal("colossus.gauntlet.tip"));
            }
        }.setUnlocalizedName("prismarine_gauntlet").setTextureName("hbm:prismarine_gauntlet").setCreativeTab(CreativeTabs.tabCombat);
        shard=new Item().setUnlocalizedName("colossus_prismarine_shard").setTextureName("hbm:colossus_prismarine_shard").setCreativeTab(CreativeTabs.tabMaterials);
        egg=new Item() {
            @Override public boolean onItemUse(ItemStack stack,EntityPlayer player,World w,int x,int y,int z,int side,float hx,float hy,float hz) {
                if(w.isRemote) return true;
                if(!player.capabilities.isCreativeMode) return false;
                EntityPrismarineColossus boss=new EntityPrismarineColossus(w);
                boss.setLocationAndAngles(x+0.5,y+1,z+0.5,player.rotationYaw,0);
                if(!ColossusConfig.enabled || !ColossusEncounters.get(w).room() || !boss.safeSpawn()) { player.addChatMessage(new ChatComponentTranslation("colossus.spawn.unsafe")); return false; }
                if(w.spawnEntityInWorld(boss)) return true;
                boss.setDead(); return false;
            }
        }.setUnlocalizedName("colossus_spawn_egg").setTextureName("hbm:colossus_spawn_egg").setCreativeTab(CreativeTabs.tabMisc);
        GameRegistry.registerItem(gauntlet,"prismarine_gauntlet");
        GameRegistry.registerItem(shard,"colossus_prismarine_shard");
        GameRegistry.registerItem(egg,"colossus_spawn_egg");
        GameRegistry.addRecipe(new ItemStack(gauntlet),"DED","GPG"," G ",'D',Items.diamond,'E',Items.emerald,'G',Items.gold_ingot,'P',Items.ender_pearl);
        GameRegistry.addShapelessRecipe(new ItemStack(Items.quartz,4),new ItemStack(shard),Blocks.sand);
    }
}
