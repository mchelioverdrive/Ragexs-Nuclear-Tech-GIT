package com.hbm.entity.logic;

import com.hbm.main.MainRegistry;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

public abstract class EntityExplosionChunkloading extends Entity implements IChunkLoader {
	private static final java.util.Set<EntityExplosionChunkloading> ticketOwners = new java.util.HashSet<EntityExplosionChunkloading>();

	private Ticket loaderTicket;
	private ChunkCoordIntPair loadedChunk;

	public EntityExplosionChunkloading(World world) {
		super(world);
	}

	@Override
	protected void entityInit() {
		// Entity construction occurs before its final position is set. Bind now,
		// force only the real central chunk from the update path.
		if(!worldObj.isRemote && com.hbm.config.BombConfig.chunkloading) init(ForgeChunkManager.requestTicket(MainRegistry.instance, worldObj, Type.ENTITY));
	}

	@Override
	public void init(Ticket ticket) {
		if(!com.hbm.config.BombConfig.chunkloading) { if(ticket != null) ForgeChunkManager.releaseTicket(ticket); clearChunkLoader(); return; }
		if(!worldObj.isRemote && ticket != null) {
			boolean restored = ticket.getEntity() == this;
			if(loaderTicket != null && loaderTicket != ticket) ForgeChunkManager.releaseTicket(loaderTicket);
			loaderTicket = ticket;
			// A restored ticket may include the old constructor's origin chunk.
			for(ChunkCoordIntPair chunk : ticket.getChunkList()) ForgeChunkManager.unforceChunk(ticket, chunk);
			ticketOwners.add(this);
			loaderTicket.bindEntity(this);
			loaderTicket.getModData();
			loadedChunk = null;
			if(restored) loadChunk((int)Math.floor(posX / 16D), (int)Math.floor(posZ / 16D));
		}
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		if(!worldObj.isRemote) loadChunk((int)Math.floor(posX / 16D), (int)Math.floor(posZ / 16D));
	}

	public void loadChunk(int x, int z) {
		if(!com.hbm.config.BombConfig.chunkloading) { clearChunkLoader(); return; }
		if(loadedChunk != null && (loadedChunk.chunkXPos != x || loadedChunk.chunkZPos != z)) {
			ForgeChunkManager.unforceChunk(loaderTicket, loadedChunk); loadedChunk = null;
		}
		if(this.loadedChunk == null && loaderTicket != null) {
			this.loadedChunk = new ChunkCoordIntPair(x, z);
			ForgeChunkManager.forceChunk(loaderTicket, loadedChunk);
		}
	}
	
	public void clearChunkLoader() {
		if(!worldObj.isRemote && loaderTicket != null) {
			ForgeChunkManager.releaseTicket(loaderTicket);
			loaderTicket = null;
			loadedChunk = null;
			ticketOwners.remove(this);
		}
	}

	public static void releaseWorldTickets(World world) {
		for(EntityExplosionChunkloading entity : ticketOwners.toArray(new EntityExplosionChunkloading[0])) {
			if(entity.worldObj == world) entity.clearChunkLoader();
		}
	}

	public static void releaseAllTickets() {
		for(EntityExplosionChunkloading entity : ticketOwners.toArray(new EntityExplosionChunkloading[0])) entity.clearChunkLoader();
		ticketOwners.clear();
	}

	@Override
	public void setDead() {
		if(!worldObj.isRemote) com.hbm.explosion.ExplosionWorkScheduler.remove(this);
		super.setDead();
		clearChunkLoader();
	}
}
