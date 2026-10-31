package com.hbm.explosion;

import java.util.HashMap;
import java.util.Map;
import com.hbm.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

/** Audited removal semantics, deliberately independent of normal mining drops. */
final class NuclearBlockBehavior {

	private static final Map<Class<?>, Boolean> nativeBaseRemoval = new HashMap<Class<?>, Boolean>();
	private static final Map<Block, Boolean> stairRemoval = new java.util.IdentityHashMap<Block, Boolean>();
	private static final String BREAK_METHOD = cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Block.class, null,
			new String[] { "breakBlock", "func_149749_a" }, World.class, int.class, int.class, int.class, Block.class, int.class).getName();
	private NuclearBlockBehavior() { }
	/** Roles are properties of block definitions, not cached resistance values. */
	static boolean ordinaryTerrain(Block b) {
		if(b == null) return false;
		return b == ModBlocks.stone_gneiss || b == ModBlocks.scorched_stone || b == ModBlocks.ore_gneiss_iron || b == ModBlocks.ore_gneiss_gold
				|| b == ModBlocks.ore_gneiss_copper || b == ModBlocks.ore_gneiss_lithium || b == ModBlocks.ore_gneiss_schrabidium
				|| b == ModBlocks.ore_gneiss_rare || b == ModBlocks.ore_gneiss_gas || b == ModBlocks.ore_coal_oil || b == ModBlocks.ore_coal_oil_burning
				|| b == Blocks.stone || b == Blocks.dirt || b == Blocks.grass || b == Blocks.sand || b == Blocks.gravel
				|| b == Blocks.sandstone || b == Blocks.clay || b == Blocks.hardened_clay || b == Blocks.stained_hardened_clay
				|| b == Blocks.netherrack || b == Blocks.end_stone || b == Blocks.mycelium || b == Blocks.soul_sand || b == ModBlocks.stone_porous
				|| b instanceof net.minecraft.block.BlockOre || b instanceof net.minecraft.block.BlockRedstoneOre
				|| b instanceof com.hbm.blocks.generic.BlockOre || b instanceof com.hbm.blocks.generic.BlockOreOutgas
				|| b.getClass() == com.hbm.blocks.generic.BlockOutgas.class && b != ModBlocks.ancient_scrap
				|| b instanceof com.hbm.blocks.generic.BlockDirt || b instanceof com.hbm.blocks.generic.WasteEarth;
	}
	static boolean constructionBarrier(Block b) {
		// Actual RNT concrete/ducrete families include metadata slabs and stair delegates.
		String name = (String)Block.blockRegistry.getNameForObject(b);
		return b instanceof com.hbm.blocks.IRadResistantBlock || name != null && (name.contains("concrete")
				|| name.contains("ducrete") || name.contains("reinforced") || name.contains("blast_door")
				|| name.contains("vault_door") || name.contains("airlock") || name.contains("seal_door")
				|| name.contains("brick_slab") || name.contains("brick_double_slab"));
	}
	static boolean weak(Block b) {
		Material m = b.getMaterial();
		return m == Material.wood || m == Material.glass || m == Material.leaves || m == Material.plants
				|| m == Material.vine || m == Material.cloth || m == Material.circuits || m == Material.gourd
				|| m == Material.snow || m == Material.craftedSnow || b == Blocks.farmland;
	}
	/** Ordinary stone and ores contribute aggregate overburden only. Never call their
	 * explosion resistance to classify or excavate them. Unknown/structural blocks
	 * retain Forge's position/metadata/explosion-context callback on the server thread. */
	static float cost(World world, Block b, int x, int y, int z, double sx, double sy, double sz) {
		if(b == Blocks.air || b.isAir(world, x, y, z)) return 0F;
		if(b == Blocks.bedrock || b.getBlockHardness(world, x, y, z) < 0F) return Float.POSITIVE_INFINITY;
		if(b.getMaterial().isLiquid()) return 2F;
		if(ordinaryTerrain(b)) return 1F;
		if(b == Blocks.obsidian) return 19F; // Retain the former stone-times-three nuclear override.
		if(!constructionBarrier(b) && weak(b)) return b.getMaterial() == Material.wood ? 5F : 1F;
		float r = b.getExplosionResistance(null, world, x, y, z, sx, sy, sz);
		// Existing protected-block threshold remains authoritative for unknown blocks.
		// RNT construction barriers are destructible by core energy; ultra-resistant
		// materials (e.g. reinforced CMB brick) remain protected even in the core.
		if(!Float.isFinite(r) || r < 0F || r >= (constructionBarrier(b) ? 2000F : ExplosionNukeGeneric.LIQUID_RESISTANCE_THRESHOLD)) return Float.POSITIVE_INFINITY;
		return Math.max(1F, r + 1F);
	}
	static float summaryCost(World world, Block b, int x, int y, int z) {
		if(b == Blocks.air || b.isAir(world, x, y, z)) return 0F;
		if(b == Blocks.bedrock || b.getBlockHardness(world, x, y, z) < 0F) return Float.POSITIVE_INFINITY;
		if(b.getMaterial().isLiquid()) return 2F;
		if(ordinaryTerrain(b)) return 1F;
		if(!constructionBarrier(b) && weak(b)) return b.getMaterial() == Material.wood ? 5F : 1F;
		// A marker locates structural barriers without capturing their potentially
		// metadata/TE/context-dependent resistance in a reusable section summary.
		return -1F;
	}
	static boolean progressive(Block block) {
		Class<?> type = block.getClass();
		if(type == net.minecraft.block.BlockStairs.class) {
			Boolean safe = stairRemoval.get(block);
			if(safe == null) {
				Block model = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(net.minecraft.block.BlockStairs.class,
						(net.minecraft.block.BlockStairs)block, "field_150149_b");
				safe = model != block && progressive(model); stairRemoval.put(block, safe);
			}
			return safe;
		}
		// Outgas' mining override is not called by setBlock. Its ore subclass has
		// base breakBlock; BlockOutgas itself has one special ancient-scrap callback.
		if(type == com.hbm.blocks.generic.BlockOre.class || type == com.hbm.blocks.generic.BlockOreOutgas.class
				|| type == com.hbm.blocks.generic.BlockDirt.class
				|| type == com.hbm.blocks.generic.BlockNTMFlower.class || type == com.hbm.blocks.generic.BlockTallPlant.class
				|| type == com.hbm.blocks.generic.BlockDeadPlant.class || type == com.hbm.blocks.BlockCrop.class
				|| type == com.hbm.blocks.generic.WasteEarth.class || type == com.hbm.blocks.generic.WasteLog.class || type == com.hbm.blocks.generic.WasteLeaves.class
				|| type == com.hbm.blocks.generic.BlockGeneric.class || type == com.hbm.blocks.BlockBase.class) return true;
		if(type == com.hbm.blocks.generic.BlockOutgas.class) return block != ModBlocks.ancient_scrap;
		if(type == Block.class) return true;
		if(!type.getName().startsWith("net.minecraft.block.")) return false;
		Boolean safe = nativeBaseRemoval.get(type);
		if(safe == null) {
			try { safe = type.getMethod(BREAK_METHOD, World.class, int.class, int.class, int.class, Block.class, int.class).getDeclaringClass() == Block.class; }
			catch(ReflectiveOperationException failure) { safe = false; }
			nativeBaseRemoval.put(type, safe);
		}
		return safe;
	}
	static boolean fragilePlant(Block block) {
		Class<?> type = block.getClass();
		return block instanceof net.minecraft.block.BlockBush || type == com.hbm.blocks.generic.BlockNTMFlower.class
				|| type == com.hbm.blocks.generic.BlockTallPlant.class || type == com.hbm.blocks.generic.BlockDeadPlant.class;
	}

	static boolean pressureTarget(Block block) {
		if(constructionBarrier(block)) return true;
		Material m = block.getMaterial();
		return m == Material.wood || m == Material.glass || m == Material.leaves || m == Material.plants
				|| m == Material.vine || m == Material.cloth || m == Material.circuits || m == Material.gourd
				|| block == Blocks.farmland || block == Blocks.brick_block || block == Blocks.cobblestone
				|| block == Blocks.stonebrick || block instanceof net.minecraft.block.BlockStairs;
	}

	/** Only attachments whose support direction is known; no radius-based culling. */
	static boolean attachedTo(World world, int x, int y, int z, int sx, int sy, int sz) {
		Block b = world.getBlock(x, y, z);
		if(b == Blocks.air || b.isOpaqueCube()) return false;
		int m = world.getBlockMetadata(x, y, z);
		if(b instanceof net.minecraft.block.BlockTorch) {
			return m == 1 && sx == x - 1 && sy == y && sz == z || m == 2 && sx == x + 1 && sy == y && sz == z
					|| m == 3 && sz == z - 1 && sx == x && sy == y || m == 4 && sz == z + 1 && sx == x && sy == y
					|| m == 5 && sx == x && sz == z && sy == y - 1;
		}
		if(b == Blocks.ladder || b == Blocks.wall_sign) {
			return sy == y && (m == 2 && sx == x && sz == z + 1 || m == 3 && sx == x && sz == z - 1
					|| m == 4 && sx == x + 1 && sz == z || m == 5 && sx == x - 1 && sz == z);
		}
		if(b == Blocks.cocoa) return sy == y && sx == x + net.minecraft.util.Direction.offsetX[m & 3] && sz == z + net.minecraft.util.Direction.offsetZ[m & 3];
		if(b instanceof net.minecraft.block.BlockTrapDoor) {
			int face = m & 3;
			return sy == y && (face == 0 && sx == x && sz == z + 1 || face == 1 && sx == x && sz == z - 1
					|| face == 2 && sx == x + 1 && sz == z || face == 3 && sx == x - 1 && sz == z);
		}
		if(b == Blocks.tripwire_hook) {
			int face = m & 3;
			return sy == y && (face == 0 && sx == x && sz == z - 1 || face == 2 && sx == x && sz == z + 1
					|| face == 1 && sx == x + 1 && sz == z || face == 3 && sx == x - 1 && sz == z);
		}
		if(b == Blocks.vine) {
			if(sy == y + 1 && sx == x && sz == z) return m == 0;
			int bit = sx == x && sz == z + 1 ? 1 : sx == x - 1 && sz == z ? 2 : sx == x && sz == z - 1 ? 4 : sx == x + 1 && sz == z ? 8 : 0;
			// A vine with another face or an attached vine above keeps its support.
			return sy == y && bit != 0 && m == bit && !(world.getBlock(x, y + 1, z) == Blocks.vine && (world.getBlockMetadata(x, y + 1, z) & bit) != 0);
		}
		if(b instanceof net.minecraft.block.BlockButton || b == Blocks.lever) {
			int face = m & 7;
			return face == 1 && sx == x - 1 && sy == y && sz == z || face == 2 && sx == x + 1 && sy == y && sz == z
					|| face == 3 && sz == z - 1 && sx == x && sy == y || face == 4 && sz == z + 1 && sx == x && sy == y
					|| (face == 5 || face == 6) && sx == x && sz == z && sy == y - 1
					|| (face == 0 || face == 7) && sx == x && sz == z && sy == y + 1;
		}
		return sx == x && sz == z && sy == y - 1 && (fragilePlant(b)
				|| b instanceof net.minecraft.block.BlockReed || b instanceof net.minecraft.block.BlockCactus
				|| b instanceof net.minecraft.block.BlockDoor || b instanceof net.minecraft.block.BlockRailBase
				|| b instanceof net.minecraft.block.BlockBasePressurePlate || b == Blocks.redstone_wire || b == Blocks.standing_sign || b == Blocks.snow_layer || b == Blocks.flower_pot);
	}
}
