package com.hbm.machine;

/** Stable identity for one lifetime of a logical machine at a world position. */
public final class MachineKey {

	public final int dimension;
	public final int x;
	public final int y;
	public final int z;
	public final long generation;

	public MachineKey(int dimension, int x, int y, int z, long generation) {
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.generation = generation;
	}

	@Override
	public int hashCode() {
		int result = dimension;
		result = 31 * result + x;
		result = 31 * result + y;
		result = 31 * result + z;
		result = 31 * result + (int) (generation ^ generation >>> 32);
		return result;
	}

	@Override
	public boolean equals(Object object) {
		if(this == object) return true;
		if(!(object instanceof MachineKey)) return false;
		MachineKey other = (MachineKey) object;
		return dimension == other.dimension && x == other.x && y == other.y && z == other.z && generation == other.generation;
	}

	@Override
	public String toString() {
		return dimension + ":" + x + "," + y + "," + z + "#" + generation;
	}
}
