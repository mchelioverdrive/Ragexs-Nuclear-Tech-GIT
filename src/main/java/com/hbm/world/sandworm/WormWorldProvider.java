package com.hbm.world.sandworm;

import com.hbm.config.SandwormConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManagerHell;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;

public class WormWorldProvider extends WorldProvider {
    public static void register() {
        int id = SandwormConfig.dimension;
        if(DimensionManager.isDimensionRegistered(id) || !DimensionManager.registerProviderType(id, WormWorldProvider.class, false))
            throw new IllegalStateException("Sandworm dimension ID " + id + " is occupied. Set an unused sandworm.dimensionID before creating a world.");
        DimensionManager.registerDimension(id, id);
    }
    @Override protected void registerWorldChunkManager() {
        worldChunkMgr = new WorldChunkManagerHell(BiomeGenBase.plains, 0.8F);
        hasNoSky = true;
        setAllowedSpawnTypes(false, false);
    }
    @Override public IChunkProvider createChunkGenerator() { return new WormChunkProvider(worldObj); }
    @Override public String getDimensionName() { return "Sandworm Interior"; }
    @Override public String getSaveFolder() { return "DIM_RNT_Sandworm"; }
    @Override public boolean canRespawnHere() { return false; }
    @Override public int getRespawnDimension(EntityPlayerMP p) { return WormEncounters.get(worldObj).returnDimension(p); }
    @Override public boolean isSurfaceWorld() { return false; }
    @Override public float calculateCelestialAngle(long time, float partial) { return 0.5F; }
    @Override protected void generateLightBrightnessTable() {
        // A faint organic ambient glow keeps the sealed interior traversable without player-placed torches.
        for(int i = 0; i <= 15; i++) { float darkness = 1F - i / 15F; lightBrightnessTable[i] = (1F - darkness) / (darkness * 3F + 1F) * 0.84F + 0.16F; }
    }
    @Override public boolean canDoRainSnowIce(net.minecraft.world.chunk.Chunk c) { return false; }
    @Override public boolean canDoLightning(net.minecraft.world.chunk.Chunk c) { return false; }
}
