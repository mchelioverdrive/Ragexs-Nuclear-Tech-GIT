package com.hbm.world.gen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.hbm.handler.ThreeInts;
import com.hbm.main.MainRegistry;
import com.hbm.util.Tuple.Pair;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.util.Constants.NBT;

public class NBTStructure {

	private static final Map<String, NBTStructure> STRUCTURES = new HashMap<String, NBTStructure>();
	private static final Map<String, Definition> DEFINITIONS = new HashMap<String, Definition>();

	public interface SpawnRule {
		boolean canSpawn(World world, int chunkX, int chunkZ);
	}

	public enum HeightStrategy {
		AVERAGE_SURFACE,
		FIXED
	}

	/** One registration supplies placement policy; the template holds block and TE data. */
	public static class Definition {
		public final String id;
		public final NBTStructure structure;
		public final int dimension;
		public final int rarity;
		public final int centerOffsetX;
		public final int centerOffsetZ;
		public final HeightStrategy heightStrategy;
		public final int fixedHeight;
		public final java.util.function.Predicate<BiomeGenBase> biomeRule;
		public final SpawnRule spawnRule;

		public Definition(String id, ResourceLocation resource, int dimension, int rarity, int centerOffsetX, int centerOffsetZ,
			HeightStrategy heightStrategy, int fixedHeight, java.util.function.Predicate<BiomeGenBase> biomeRule, SpawnRule spawnRule) {
			if(rarity < 1) throw new IllegalArgumentException("Structure rarity must be positive");
			this.id = id;
			this.structure = getOrLoad(resource);
			this.dimension = dimension;
			this.rarity = rarity;
			this.centerOffsetX = centerOffsetX;
			this.centerOffsetZ = centerOffsetZ;
			this.heightStrategy = heightStrategy;
			this.fixedHeight = fixedHeight;
			this.biomeRule = biomeRule;
			this.spawnRule = spawnRule;
			DEFINITIONS.put(id, this);
		}
	}

	private final String structureName;
	private ThreeInts size;
	private BlockState[][][] blocks;
	private HashMap<Short, Short> itemPalette;
	private boolean loaded;

	public static synchronized NBTStructure getOrLoad(ResourceLocation resource) {
		NBTStructure structure = STRUCTURES.get(resource.toString());
		return structure != null ? structure : new NBTStructure(resource);
	}

	public NBTStructure(ResourceLocation resource) {
		structureName = resource.toString();
		try {
			String path = "assets/" + resource.getResourceDomain() + "/" + resource.getResourcePath();
			InputStream stream = NBTStructure.class.getClassLoader().getResourceAsStream(path);
			if(stream == null) throw new IOException("Missing NBT structure: " + resource);
			loadStructure(stream);
			STRUCTURES.put(structureName, this);
		} catch(IOException e) {
			throw new IllegalStateException("IO Exception loading NBT resource", e);
		}
	}

	public static void register() {
		MapGenStructureIO.registerStructure(Start.class, "RTMNBTStructure");
		MapGenStructureIO.func_143031_a(Component.class, "RTMNBTComponent");
	}

	// Saves a selected area into an NBT structure (+ some of our non-standard stuff to support 1.7.10)
	public static void saveArea(String filename, World world, int x1, int y1, int z1, int x2, int y2, int z2, Set<Pair<Block, Integer>> exclude) {
		NBTTagCompound structure = new NBTTagCompound();
		NBTTagList nbtBlocks = new NBTTagList();
		NBTTagList nbtPalette = new NBTTagList();
		NBTTagList nbtItemPalette = new NBTTagList();

		// Quick access hash slinging slashers
		Map<Pair<Block, Integer>, Integer> palette = new HashMap<>();
		Map<Short, Integer> itemPalette = new HashMap<>();

		structure.setInteger("version", 1);

		int ox = Math.min(x1, x2);
		int oy = Math.min(y1, y2);
		int oz = Math.min(z1, z2);

		for(int x = ox; x <= Math.max(x1, x2); x++) {
			for(int y = oy; y <= Math.max(y1, y2); y++) {
				for(int z = oz; z <= Math.max(z1, z2); z++) {
					Pair<Block, Integer> block = new Pair<Block, Integer>(world.getBlock(x, y, z), world.getBlockMetadata(x, y, z));

					if(exclude.contains(block)) continue;

					int paletteId = palette.size();
					if(palette.containsKey(block)) {
						paletteId = palette.get(block);
					} else {
						palette.put(block, paletteId);

						NBTTagCompound nbtBlock = new NBTTagCompound();
						nbtBlock.setString("Name", GameRegistry.findUniqueIdentifierFor(block.key).toString());
						
						NBTTagCompound nbtProp = new NBTTagCompound();
						nbtProp.setString("meta", block.value.toString());

						nbtBlock.setTag("Properties", nbtProp);

						nbtPalette.appendTag(nbtBlock);
					}

					NBTTagCompound nbtBlock = new NBTTagCompound();
					nbtBlock.setInteger("state", paletteId);

					NBTTagList nbtPos = new NBTTagList();
					nbtPos.appendTag(new NBTTagInt(x - ox));
					nbtPos.appendTag(new NBTTagInt(y - oy));
					nbtPos.appendTag(new NBTTagInt(z - oz));

					nbtBlock.setTag("pos", nbtPos);

					TileEntity te = world.getTileEntity(x, y, z);
					if(te != null) {
						NBTTagCompound nbt = new NBTTagCompound();
						te.writeToNBT(nbt);

						nbt.setInteger("x", nbt.getInteger("x") - ox);
						nbt.setInteger("y", nbt.getInteger("y") - oy);
						nbt.setInteger("z", nbt.getInteger("z") - oz);

						nbtBlock.setTag("nbt", nbt);

						String itemKey = null;
						if(nbt.hasKey("items")) itemKey = "items";
						if(nbt.hasKey("Items")) itemKey = "Items";

						if(nbt.hasKey(itemKey)) {
							NBTTagList items = nbt.getTagList("items", NBT.TAG_COMPOUND);
							for(int i = 0; i < items.tagCount(); i++) {
								NBTTagCompound item = items.getCompoundTagAt(i);
								short id = item.getShort("id");
								String name = GameRegistry.findUniqueIdentifierFor(Item.getItemById(id)).toString();

								if(!itemPalette.containsKey(id)) {
									int itemPaletteId = itemPalette.size();
									itemPalette.put(id, itemPaletteId);

									NBTTagCompound nbtItem = new NBTTagCompound();
									nbtItem.setShort("ID", id);
									nbtItem.setString("Name", name);

									nbtItemPalette.appendTag(nbtItem);
								}
							}
						}
					}

					nbtBlocks.appendTag(nbtBlock);
				}
			}
		}

		structure.setTag("blocks", nbtBlocks);
		structure.setTag("palette", nbtPalette);
		structure.setTag("itemPalette", nbtItemPalette);

		NBTTagList nbtSize = new NBTTagList();
		nbtSize.appendTag(new NBTTagInt(Math.abs(x1 - x2) + 1));
		nbtSize.appendTag(new NBTTagInt(Math.abs(y1 - y2) + 1));
		nbtSize.appendTag(new NBTTagInt(Math.abs(z1 - z2) + 1));
		structure.setTag("size", nbtSize);

		structure.setTag("entities", new NBTTagList());

		try {
			File structureDirectory = new File(Minecraft.getMinecraft().mcDataDir, "structures");
			structureDirectory.mkdir();

			File structureFile = new File(structureDirectory, filename);

			CompressedStreamTools.writeCompressed(structure, new FileOutputStream(structureFile));
		} catch (Exception ex) {
			MainRegistry.logger.warn("Failed to save NBT structure", ex);
		}
	}

	private void loadStructure(InputStream inputStream) {
		try {
			NBTTagCompound data = CompressedStreamTools.readCompressed(inputStream);
			size = parsePos(data.getTagList("size", NBT.TAG_INT));

			NBTTagList paletteList = data.getTagList("palette", NBT.TAG_COMPOUND);
			BlockDefinition[] palette = new BlockDefinition[paletteList.tagCount()];
			for(int i = 0; i < paletteList.tagCount(); i++) {
				NBTTagCompound entry = paletteList.getCompoundTagAt(i);
				String blockName = entry.getString("Name");
				String metaValue = entry.getCompoundTag("Properties").getString("meta");
				int meta;
				try {
					meta = Integer.parseInt(metaValue);
				} catch(NumberFormatException ex) {
					MainRegistry.logger.info("Failed to parse: " + metaValue);
					meta = 0;
				}
				palette[i] = new BlockDefinition(blockName, meta);
			}

			if(data.hasKey("itemPalette")) {
				itemPalette = new HashMap<Short, Short>();
				NBTTagList itemPaletteList = data.getTagList("itemPalette", NBT.TAG_COMPOUND);
				for(int i = 0; i < itemPaletteList.tagCount(); i++) {
					NBTTagCompound entry = itemPaletteList.getCompoundTagAt(i);
					Item item = (Item)Item.itemRegistry.getObject(entry.getString("Name"));
					if(item != null) itemPalette.put(entry.getShort("ID"), (short)Item.getIdFromItem(item));
				}
			}

			blocks = new BlockState[size.x][size.y][size.z];
			NBTTagList blockList = data.getTagList("blocks", NBT.TAG_COMPOUND);
			for(int i = 0; i < blockList.tagCount(); i++) {
				NBTTagCompound entry = blockList.getCompoundTagAt(i);
				ThreeInts pos = parsePos(entry.getTagList("pos", NBT.TAG_INT));
				BlockState state = new BlockState(palette[entry.getInteger("state")]);
				if(entry.hasKey("nbt")) state.nbt = entry.getCompoundTag("nbt");
				blocks[pos.x][pos.y][pos.z] = state;
			}
			loaded = true;

		} catch(IOException e) {
			throw new IllegalStateException("IO Exception reading NBT Structure format", e);
		} finally {
			try {
				inputStream.close();
			} catch(IOException e) {
				// hush
			}
		}
	}

	public void build(World world, int x, int y, int z) {
		if(!loaded) {
			MainRegistry.logger.info("NBTStructure is invalid");
			return;
		}

		x -= size.x / 2;
		z -= size.z / 2;
		for(int bx = 0; bx < size.x; bx++) {
			for(int bz = 0; bz < size.z; bz++) {
				for(int by = 0; by < size.y; by++) {
					placeBlock(world, x + bx, y + by, z + bz, blocks[bx][by][bz]);
				}
			}
		}
	}

	private boolean build(World world, StructureBoundingBox totalBounds, StructureBoundingBox generatingBounds) {
		if(!loaded) return false;
		int minX = Math.max(generatingBounds.minX - totalBounds.minX, 0);
		int maxX = Math.min(generatingBounds.maxX - totalBounds.minX + 1, size.x);
		int minZ = Math.max(generatingBounds.minZ - totalBounds.minZ, 0);
		int maxZ = Math.min(generatingBounds.maxZ - totalBounds.minZ + 1, size.z);

		for(int bx = minX; bx < maxX; bx++) {
			for(int bz = minZ; bz < maxZ; bz++) {
				for(int by = 0; by < size.y; by++) {
					placeBlock(world, totalBounds.minX + bx, totalBounds.minY + by, totalBounds.minZ + bz, blocks[bx][by][bz]);
				}
			}
		}
		return true;
	}

	private void placeBlock(World world, int x, int y, int z, BlockState state) {
		if(state == null) return;
		world.setBlock(x, y, z, state.definition.block, state.definition.meta, 2);
		if(state.nbt == null) return;

		NBTTagCompound nbt = (NBTTagCompound)state.nbt.copy();
		if(itemPalette != null) relinkItems(itemPalette, nbt);
		nbt.setInteger("x", x);
		nbt.setInteger("y", y);
		nbt.setInteger("z", z);
		TileEntity tile = TileEntity.createAndLoadEntity(nbt);
		if(tile != null) world.setTileEntity(x, y, z, tile);
	}

	// What a fucken mess, why even implement the IntArray NBT if ye aint gonna use it Moe Yang?
	private ThreeInts parsePos(NBTTagList pos) {
		NBTBase xb = (NBTBase)pos.tagList.get(0);
		int x = ((NBTTagInt)xb).func_150287_d();
		NBTBase yb = (NBTBase)pos.tagList.get(1);
		int y = ((NBTTagInt)yb).func_150287_d();
		NBTBase zb = (NBTBase)pos.tagList.get(2);
		int z = ((NBTTagInt)zb).func_150287_d();

		return new ThreeInts(x, y, z);
	}

	// NON-STANDARD, items are serialized with IDs, which will differ from world to world!
	// So our fixed exporter adds an itemPalette, please don't hunt me down for fucking with the spec
	private void relinkItems(HashMap<Short, Short> palette, NBTTagCompound nbt) {
		NBTTagList items = null;
		if(nbt.hasKey("items"))
			items = nbt.getTagList("items", NBT.TAG_COMPOUND);
		if(nbt.hasKey("Items"))
			items = nbt.getTagList("Items", NBT.TAG_COMPOUND);

		if(items == null) return;

		for(int i = 0; i < items.tagCount(); i++) {
			NBTTagCompound item = items.getCompoundTagAt(i);
			item.setShort("id", palette.get(item.getShort("id")));
		}
	}

	private static class BlockDefinition {

		Block block;
		int meta;

		BlockDefinition(String name, int meta) {
			this.block = Block.getBlockFromName(name);
			this.meta = meta;
		}

	}

	private static class BlockState {
		final BlockDefinition definition;
		NBTTagCompound nbt;

		BlockState(BlockDefinition definition) {
			this.definition = definition;
		}
	}

	public static class Component extends StructureComponent {
		private NBTStructure structure;
		private Definition definition;
		private boolean heightSet;

		public Component() { }

		public Component(Definition definition, int centerX, int centerZ) {
			this.definition = definition;
			this.structure = definition.structure;
			int minX = centerX - structure.size.x / 2;
			int minZ = centerZ - structure.size.z / 2;
			boundingBox = new StructureBoundingBox(minX, 0, minZ, minX + structure.size.x - 1, 255, minZ + structure.size.z - 1);
			if(definition.heightStrategy == HeightStrategy.FIXED) setHeight(definition.fixedHeight);
		}

		private void setHeight(int y) {
			y = Math.max(1, Math.min(255 - structure.size.y, y));
			boundingBox.minY = y;
			boundingBox.maxY = y + structure.size.y - 1;
			heightSet = true;
		}

		@Override
		protected void func_143012_a(NBTTagCompound nbt) {
			nbt.setString("structure", structure.structureName);
			if(definition != null) nbt.setString("definition", definition.id);
			nbt.setBoolean("heightSet", heightSet);
		}

		@Override
		protected void func_143011_b(NBTTagCompound nbt) {
			String name = nbt.getString("structure");
			structure = STRUCTURES.get(name);
			if(structure == null) structure = getOrLoad(new ResourceLocation(name));
			definition = DEFINITIONS.get(nbt.getString("definition"));
			heightSet = nbt.getBoolean("heightSet");
		}

		@Override
		public boolean addComponentParts(World world, Random rand, StructureBoundingBox box) {
			if(structure == null) return false;
			if(!heightSet) {
				int total = 0;
				int samples = 0;
				for(int z = box.minZ; z <= box.maxZ; z++) {
					for(int x = box.minX; x <= box.maxX; x++) {
						total += world.getTopSolidOrLiquidBlock(x, z);
						samples++;
					}
				}
				setHeight((samples > 0 ? total / samples : 64) - 1);
			}
			return structure.build(world, boundingBox, box);
		}
	}

	public static class Start extends StructureStart {
		public Start() { }

		@SuppressWarnings("unchecked")
		public Start(Definition definition, int chunkX, int chunkZ) {
			super(chunkX, chunkZ);
			components.add(new Component(definition, (chunkX << 4) + definition.centerOffsetX, (chunkZ << 4) + definition.centerOffsetZ));
			updateBoundingBox();
		}
	}

	public static class RegisteredStructureGenerator extends MapGenStructure {
		private final Definition definition;

		public RegisteredStructureGenerator(Definition definition) {
			this.definition = definition;
		}

		public void generateStructures(World world, Random rand, IChunkProvider chunkProvider, int chunkX, int chunkZ) {
			try {
				func_151539_a(chunkProvider, world, chunkX, chunkZ, null);
				generateStructuresInChunk(world, rand, chunkX, chunkZ);
			} finally {
				worldObj = null;
			}
		}

		@Override
		public String func_143025_a() {
			return definition.id;
		}

		@Override
		protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
			if(worldObj.provider.dimensionId != definition.dimension) return false;
			if(definition.biomeRule != null && !definition.biomeRule.test(worldObj.getWorldChunkManager().getBiomeGenAt((chunkX << 4) + 8, (chunkZ << 4) + 8))) return false;
			return (definition.rarity == 1 || rand.nextInt(definition.rarity) == 0)
				&& (definition.spawnRule == null || definition.spawnRule.canSpawn(worldObj, chunkX, chunkZ));
		}

		@Override
		protected StructureStart getStructureStart(int chunkX, int chunkZ) {
			return new Start(definition, chunkX, chunkZ);
		}
	}

}
