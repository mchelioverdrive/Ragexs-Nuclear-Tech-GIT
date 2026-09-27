package api.hbm.energymk2;

import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.hbm.util.Compat;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/** If it sends energy, use this */
public interface IEnergyProviderMK2 extends IEnergyHandlerMK2 {

	/** Extracts stored energy. Caller must not request more than is available. */
	public default void extractEnergyQuanta(long energyQuanta) {
		if(LegacyEnergyOverrides.of(this.getClass()).providerExtract) {
			this.usePower(EnergyUnits.quantaToLegacyHe(energyQuanta));
			return;
		}
		long previous = this.getStoredEnergyQuanta();
		this.setStoredEnergyQuanta(this.getStoredEnergyQuanta() - energyQuanta);
		if(this.getStoredEnergyQuanta() != previous) PowerNetMK2.markProviderSupplyDirty(this);
	}

	@Deprecated public default void usePower(long legacyHe) { extractEnergyQuanta(EnergyUnits.legacyHeToQuanta(legacyHe)); }
	@Deprecated public default long getProviderSpeed() { return EnergyUnits.quantaToLegacyHe(getMaxOutputQuantaPerTick()); }
	
	public default long getMaxOutputQuantaPerTick() {
		if(LegacyEnergyOverrides.of(this.getClass()).providerSpeed) return EnergyUnits.legacyHeToQuanta(this.getProviderSpeed());
		return this.getEnergyCapacityQuanta();
	}

	/** Registers a persistent conductor path without performing an adjacent direct transfer. */
	public default void registerPowerConnection(World world, int x, int y, int z, ForgeDirection dir) {
		PowerNetEndpointRegistry.attachProvider(this, world, x, y, z, dir);
	}

	/** Preserves the legacy direct-neighbor transfer cadence without repeating graph attachment work. */
	public default void providePowerToDirectReceiver(World world, int x, int y, int z, ForgeDirection dir) {
		TileEntity te = Compat.getTileStandard(world, x, y, z);
		if(te instanceof IEnergyReceiverMK2 && te != this) {
			IEnergyReceiverMK2 rec = (IEnergyReceiverMK2) te;
			if(rec.canConnect(dir.getOpposite())) {
				long provides = Math.min(this.getStoredEnergyQuanta(), this.getMaxOutputQuantaPerTick());
				long receives = Math.min(rec.getEnergyCapacityQuanta() - rec.getStoredEnergyQuanta(), rec.getMaxInputQuantaPerTick());
				long toTransfer = Math.min(provides, receives);
				toTransfer -= rec.receiveEnergyQuanta(toTransfer);
				this.extractEnergyQuanta(toTransfer);
			}
		}
	}

	public default void tryProvide(World world, int x, int y, int z, ForgeDirection dir) {

		boolean red = PowerNetEndpointRegistry.attachProvider(this, world, x, y, z, dir);
		this.providePowerToDirectReceiver(world, x, y, z, dir);
		
		if(particleDebug) {
			NBTTagCompound data = new NBTTagCompound();
			data.setString("type", "network");
			data.setString("mode", "power");
			double posX = x + 0.5 - dir.offsetX * 0.5 + world.rand.nextDouble() * 0.5 - 0.25;
			double posY = y + 0.5 - dir.offsetY * 0.5 + world.rand.nextDouble() * 0.5 - 0.25;
			double posZ = z + 0.5 - dir.offsetZ * 0.5 + world.rand.nextDouble() * 0.5 - 0.25;
			data.setDouble("mX", dir.offsetX * (red ? 0.025 : 0.1));
			data.setDouble("mY", dir.offsetY * (red ? 0.025 : 0.1));
			data.setDouble("mZ", dir.offsetZ * (red ? 0.025 : 0.1));
			PacketDispatcher.wrapper.sendToAllAround(new AuxParticlePacketNT(data, posX, posY, posZ), new TargetPoint(world.provider.dimensionId, posX, posY, posZ, 25));
		}
	}
}
