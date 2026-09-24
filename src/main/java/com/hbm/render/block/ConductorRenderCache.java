package com.hbm.render.block;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.lib.Library;

import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.model.obj.GroupObject;
import net.minecraftforge.client.model.obj.WavefrontObject;

/** Immutable render-shape selection for ordinary six-direction conductors. */
final class ConductorRenderCache {

	static final int POS_X = 32;
	static final int NEG_X = 16;
	static final int POS_Y = 8;
	static final int NEG_Y = 4;
	static final int POS_Z = 2;
	static final int NEG_Z = 1;

	private ConductorRenderCache() { }

	static int getCableMask(IBlockAccess world, int x, int y, int z) {
		int mask = 0;
		if(Library.canConnect(world, x + 1, y, z, Library.POS_X)) mask |= POS_X;
		if(Library.canConnect(world, x - 1, y, z, Library.NEG_X)) mask |= NEG_X;
		if(Library.canConnect(world, x, y + 1, z, Library.POS_Y)) mask |= POS_Y;
		if(Library.canConnect(world, x, y - 1, z, Library.NEG_Y)) mask |= NEG_Y;
		if(Library.canConnect(world, x, y, z + 1, Library.POS_Z)) mask |= POS_Z;
		if(Library.canConnect(world, x, y, z - 1, Library.NEG_Z)) mask |= NEG_Z;
		return mask;
	}

	static int getFluidMask(IBlockAccess world, int x, int y, int z, FluidType type) {
		int mask = 0;
		if(Library.canConnectFluid(world, x + 1, y, z, Library.POS_X, type)) mask |= POS_X;
		if(Library.canConnectFluid(world, x - 1, y, z, Library.NEG_X, type)) mask |= NEG_X;
		if(Library.canConnectFluid(world, x, y + 1, z, Library.POS_Y, type)) mask |= POS_Y;
		if(Library.canConnectFluid(world, x, y - 1, z, Library.NEG_Y, type)) mask |= NEG_Y;
		if(Library.canConnectFluid(world, x, y, z + 1, Library.POS_Z, type)) mask |= POS_Z;
		if(Library.canConnectFluid(world, x, y, z - 1, Library.NEG_Z, type)) mask |= NEG_Z;
		return mask;
	}

	static GroupObject[][] buildCableShapes(WavefrontObject model) {
		GroupObject[][] shapes = new GroupObject[64][];
		for(int mask = 0; mask < shapes.length; mask++) {
			List<GroupObject> groups = new ArrayList<GroupObject>(7);
			if(mask == (POS_X | NEG_X)) {
				add(model, groups, "CX");
			} else if(mask == (POS_Y | NEG_Y)) {
				add(model, groups, "CY");
			} else if(mask == (POS_Z | NEG_Z)) {
				add(model, groups, "CZ");
			} else {
				add(model, groups, "Core");
				if((mask & POS_X) != 0) add(model, groups, "posX");
				if((mask & NEG_X) != 0) add(model, groups, "negX");
				if((mask & POS_Y) != 0) add(model, groups, "posY");
				if((mask & NEG_Y) != 0) add(model, groups, "negY");
				if((mask & NEG_Z) != 0) add(model, groups, "posZ");
				if((mask & POS_Z) != 0) add(model, groups, "negZ");
			}
			shapes[mask] = groups.toArray(new GroupObject[groups.size()]);
		}
		return shapes;
	}

	static GroupObject[][] buildPipeShapes(WavefrontObject model) {
		GroupObject[][] shapes = new GroupObject[64][];
		for(int mask = 0; mask < shapes.length; mask++) {
			List<GroupObject> groups = new ArrayList<GroupObject>(14);
			boolean pX = (mask & POS_X) != 0;
			boolean nX = (mask & NEG_X) != 0;
			boolean pY = (mask & POS_Y) != 0;
			boolean nY = (mask & NEG_Y) != 0;
			boolean pZ = (mask & POS_Z) != 0;
			boolean nZ = (mask & NEG_Z) != 0;

			if(mask == 0) {
				add(model, groups, "pX", "nX", "pY", "nY", "pZ", "nZ");
			} else if(mask == POS_X || mask == NEG_X) {
				add(model, groups, "pX", "nX");
			} else if(mask == POS_Y || mask == NEG_Y) {
				add(model, groups, "pY", "nY");
			} else if(mask == POS_Z || mask == NEG_Z) {
				add(model, groups, "pZ", "nZ");
			} else {
				if(pX) add(model, groups, "pX");
				if(nX) add(model, groups, "nX");
				if(pY) add(model, groups, "pY");
				if(nY) add(model, groups, "nY");
				if(pZ) add(model, groups, "nZ");
				if(nZ) add(model, groups, "pZ");

				if(!pX && !pY && !pZ) add(model, groups, "ppn");
				if(!pX && !pY && !nZ) add(model, groups, "ppp");
				if(!nX && !pY && !pZ) add(model, groups, "npn");
				if(!nX && !pY && !nZ) add(model, groups, "npp");
				if(!pX && !nY && !pZ) add(model, groups, "pnn");
				if(!pX && !nY && !nZ) add(model, groups, "pnp");
				if(!nX && !nY && !pZ) add(model, groups, "nnn");
				if(!nX && !nY && !nZ) add(model, groups, "nnp");
			}
			shapes[mask] = groups.toArray(new GroupObject[groups.size()]);
		}
		return shapes;
	}

	static GroupObject[] groups(WavefrontObject model, String... names) {
		List<GroupObject> groups = new ArrayList<GroupObject>(names.length);
		add(model, groups, names);
		return groups.toArray(new GroupObject[groups.size()]);
	}

	private static void add(WavefrontObject model, List<GroupObject> groups, String... names) {
		for(String name : names) {
			for(GroupObject group : model.groupObjects) {
				if(group.name.equals(name)) {
					groups.add(group);
					break;
				}
			}
		}
	}
}
