package com.hbm.world.gen.structure;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.hbm.blocks.ModBlocks;
import com.hbm.config.GeneralConfig;
import com.hbm.config.WorldConfig;
import com.hbm.world.dungeon.AncientTomb;
import com.hbm.world.dungeon.Antenna;
import com.hbm.world.dungeon.ArcticVault;
import com.hbm.world.dungeon.Barrel;
import com.hbm.world.dungeon.DesertAtom001;
import com.hbm.world.dungeon.Factory;
import com.hbm.world.dungeon.LibraryDungeon;
import com.hbm.world.dungeon.Radio01;
import com.hbm.world.dungeon.Relay;
import com.hbm.world.dungeon.Satellite;
import com.hbm.world.generator.CellularDungeonFactory;
import com.hbm.world.generator.JungleDungeon;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

/** Persistent starts for the Java-built structures that predate NBTStructure. */
public class LegacyStructureGenerator implements IWorldGenerator {

	public enum Kind {
		RADIO(-1, 10, 0, 25), ANTENNA(0, 2, 0, 2), ATOM(0, 40, 0, 33),
		LIBRARY(0, 8, 0, 10), RELAY(0, 11, 0, 15), SATELLITE(0, 24, 0, 30),
		FACTORY(0, 19, 0, 29), BARREL(0, 4, 0, 6), VAULT(-5, 5, -5, 5),
		TOMB(-29, 29, -29, 29), JUNGLE(-62, 38, -62, 38);

		final int minX, maxX, minZ, maxZ;
		Kind(int minX, int maxX, int minZ, int maxZ) {
			this.minX = minX;
			this.maxX = maxX;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}

		int rarity() {
			switch(this) {
			case RADIO: return WorldConfig.radioStructure;
			case ANTENNA: return WorldConfig.antennaStructure;
			case ATOM: return WorldConfig.atomStructure;
			case LIBRARY: return WorldConfig.dungeonStructure;
			case RELAY: return WorldConfig.relayStructure;
			case SATELLITE: return WorldConfig.satelliteStructure;
			case FACTORY: return WorldConfig.factoryStructure;
			case BARREL: return WorldConfig.barrelStructure;
			case VAULT: return WorldConfig.arcticStructure;
			case TOMB: return WorldConfig.pyramidStructure;
			case JUNGLE: return WorldConfig.jungleStructure;
			default: return 0;
			}
		}

		boolean accepts(BiomeGenBase biome) {
			switch(this) {
			case RADIO: return biome == BiomeGenBase.plains || biome == BiomeGenBase.desert;
			case ANTENNA: return biome.temperature >= 0.4F && biome.rainfall <= 0.6F;
			case ATOM:
			case BARREL: return biome.temperature >= 1.5F && !biome.canSpawnLightningBolt();
			case RELAY:
			case SATELLITE: return biome.temperature == 0.5F || biome.temperature == 2.0F;
			case TOMB: return biome.temperature >= 2.0F && !biome.canSpawnLightningBolt();
			case JUNGLE: return biome == BiomeGenBase.jungle || biome == BiomeGenBase.jungleEdge || biome == BiomeGenBase.jungleHills;
			default: return true;
			}
		}
	}
	private static final Kind[] KINDS = Kind.values();

	private LegacyMapGen generator;
	private WeakReference<World> world = new WeakReference<World>(null);

	public static void register() {
		MapGenStructureIO.registerStructure(Start.class, "RTMLegacyStart");
		MapGenStructureIO.func_143031_a(Component.class, "RTMLegacyComponent");
	}

	@Override
	public void generate(Random rand, int chunkX, int chunkZ, World current, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
		if(current.provider.dimensionId != 0 || !dungeonsEnabled(current)) return;
		boolean anyEnabled = false;
		for(Kind kind : KINDS) if(kind.rarity() > 0) { anyEnabled = true; break; }
		if(!anyEnabled) return;
		if(world.get() != current) {
			generator = new LegacyMapGen();
			world = new WeakReference<World>(current);
		}
		generator.generateStructures(current, rand, chunkProvider, chunkX, chunkZ);
	}

	private static boolean dungeonsEnabled(World world) {
		return GeneralConfig.enableDungeons == 1 || GeneralConfig.enableDungeons != 0 && world.getWorldInfo().isMapFeaturesEnabled();
	}

	public static class LegacyMapGen extends MapGenStructure {
		LegacyMapGen() { range = 5; }

		void generateStructures(World world, Random rand, IChunkProvider provider, int chunkX, int chunkZ) {
			try {
				func_151539_a(provider, world, chunkX, chunkZ, null);
				generateStructuresInChunk(world, rand, chunkX, chunkZ);
			} finally {
				worldObj = null;
			}
		}

		@Override public String func_143025_a() { return "RTMLegacyStructures"; }

		private static boolean chosen(World world, Kind kind, int chunkX, int chunkZ) {
			int rarity = kind.rarity();
			if(rarity <= 0) return false;
			long choice = world.getSeed() + (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L + (long)kind.ordinal() * 0x9E3779B97F4A7C15L;
			choice ^= choice >>> 33;
			choice *= 0xff51afd7ed558ccdL;
			choice ^= choice >>> 33;
			return rarity == 1 || (choice & Long.MAX_VALUE) % rarity == 0;
		}

		@Override
		protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
			if(!dungeonsEnabled(worldObj)) return false;
			BiomeGenBase biome = null;
			for(Kind kind : KINDS) {
				if(!chosen(worldObj, kind, chunkX, chunkZ)) continue;
				if(biome == null) biome = worldObj.getWorldChunkManager().getBiomeGenAt(chunkX * 16, chunkZ * 16);
				if(kind.accepts(biome)) return true;
			}
			return false;
		}

		@Override
		protected StructureStart getStructureStart(int chunkX, int chunkZ) {
			return new Start(worldObj, chunkX, chunkZ, rand);
		}
	}

	public static class Start extends StructureStart {
		public Start() { }
		@SuppressWarnings("unchecked")
		Start(World world, int chunkX, int chunkZ, Random rand) {
			super(chunkX, chunkZ);
			BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(chunkX * 16, chunkZ * 16);
			for(Kind kind : KINDS) {
				if(LegacyMapGen.chosen(world, kind, chunkX, chunkZ) && kind.accepts(biome))
					components.add(new Component(kind, (chunkX << 4) + rand.nextInt(16), (chunkZ << 4) + rand.nextInt(16), rand.nextLong(), rand.nextInt(256)));
			}
			updateBoundingBox();
		}
	}

	public static class Component extends StructureComponent {
		private Kind kind;
		private int anchorX, anchorZ, groundY, libraryY;
		private long seed;
		private boolean heightSet;
		private transient SoftReference<Map<Long, List<RecordingStructureWorld.Placement>>> layout;

		public Component() { }
		Component(Kind kind, int x, int z, long seed, int libraryY) {
			this.kind = kind;
			this.anchorX = x;
			this.anchorZ = z;
			this.seed = seed;
			this.libraryY = libraryY;
			boundingBox = new StructureBoundingBox(x + kind.minX, 0, z + kind.minZ, x + kind.maxX, 255, z + kind.maxZ);
		}

		@Override
		protected void func_143012_a(NBTTagCompound nbt) {
			nbt.setString("kind", kind.name());
			nbt.setInteger("anchorX", anchorX);
			nbt.setInteger("anchorZ", anchorZ);
			nbt.setInteger("groundY", groundY);
			nbt.setInteger("libraryY", libraryY);
			nbt.setLong("seed", seed);
			nbt.setBoolean("heightSet", heightSet);
		}

		@Override
		protected void func_143011_b(NBTTagCompound nbt) {
			kind = Kind.valueOf(nbt.getString("kind"));
			anchorX = nbt.getInteger("anchorX");
			anchorZ = nbt.getInteger("anchorZ");
			groundY = nbt.getInteger("groundY");
			libraryY = nbt.getInteger("libraryY");
			seed = nbt.getLong("seed");
			heightSet = nbt.getBoolean("heightSet");
		}

		@Override
		public boolean addComponentParts(World world, Random ignored, StructureBoundingBox box) {
			if(!heightSet) {
				int total = 0, count = 0;
				for(int x = box.minX; x <= box.maxX; x++) for(int z = box.minZ; z <= box.maxZ; z++) {
					total += world.getHeightValue(x, z);
					count++;
				}
				groundY = count == 0 ? 64 : total / count;
				heightSet = true;
			}
			Map<Long, List<RecordingStructureWorld.Placement>> blocks = layout == null ? null : layout.get();
			if(blocks == null) {
				BiomeGenBase biome = world.getWorldChunkManager().getBiomeGenAt(anchorX, anchorZ);
				Block top = biome.topBlock == null ? Blocks.grass : biome.topBlock;
				RecordingStructureWorld recording = new RecordingStructureWorld(world, seed, groundY, top, kind == Kind.LIBRARY);
				Random random = new Random(seed);
				switch(kind) {
				case RADIO: new Radio01().generate(recording, random, anchorX, groundY, anchorZ); break;
				case ANTENNA: new Antenna().generate(recording, random, anchorX, groundY, anchorZ); break;
				case ATOM: new DesertAtom001().generate(recording, random, anchorX, groundY, anchorZ); break;
				case LIBRARY: new LibraryDungeon().generate(recording, random, anchorX, libraryY, anchorZ); break;
				case RELAY: new Relay().generate(recording, random, anchorX, groundY, anchorZ); break;
				case SATELLITE: new Satellite().generate(recording, random, anchorX, groundY, anchorZ); break;
				case FACTORY: new Factory().generate(recording, random, anchorX, groundY, anchorZ); break;
				case BARREL: new Barrel().generate(recording, random, anchorX, groundY, anchorZ); break;
				case VAULT: new ArcticVault().trySpawn(recording, anchorX, 16 + random.nextInt(32), anchorZ); break;
				case TOMB: new AncientTomb().build(recording, random, anchorX, groundY, anchorZ); break;
				case JUNGLE:
					for(int y = 20; y <= 28; y += 4) {
						JungleDungeon jungle = CellularDungeonFactory.createJungle();
						jungle.generate(recording, anchorX, y, anchorZ, random);
					}
					for(int i = 0; i < 3; i++) recording.setBlock(anchorX, groundY + i, anchorZ, ModBlocks.deco_titanium);
					recording.setBlock(anchorX, groundY + 3, anchorZ, Blocks.redstone_block);
					break;
				}
				recording.runQueuedJobs();
				blocks = RecordingStructureWorld.partition(recording.snapshot());
				layout = new SoftReference<Map<Long, List<RecordingStructureWorld.Placement>>>(blocks);
			}
			RecordingStructureWorld.placeSlice(world, box, blocks);
			return true;
		}
	}
}
