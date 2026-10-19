package com.hbm.tileentity.machine.oil;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hbm.blocks.BlockDummyable;
import com.hbm.blocks.ModBlocks;
import com.hbm.inventory.container.ContainerMachineOilWell;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.gui.GUIMachineOilWell;
import com.hbm.items.machine.ItemMachineUpgrade.UpgradeType;
import com.hbm.tileentity.IConfigurableMachine;
import com.hbm.tileentity.IUpgradeInfoProvider;
import com.hbm.util.BobMathUtil;
import com.hbm.util.I18nUtil;
import com.hbm.util.fauxpointtwelve.DirPos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

public class TileEntityMachinePumpjack extends TileEntityOilDrillBase {

	protected static int maxPower = 250_000;
	protected static int consumption = 300;
	protected static int delay = 25;
	protected static int oilPerDeposit = 1;
	protected static int oilPerDunaDeposit = 1;
	protected static int gasPerDepositMin = 1;
	protected static int gasPerDepositMax = 1;
	protected static double drainChance = 0.025D;
	protected static double drainChanceDuna = 0.05D; //essentially, duna is supposed to produce weaker oil than the overworld.

	protected static int oilPerBedrockDepsoit = 1;
	protected static int gasPerBedrockDepositMin = 1;
	protected static int gasPerBedrockDepositMax = 1;

	// Gas from pure natgas deposits
	protected static int gasPerDeposit = 200;
	protected static int petgasPerDepositMin = 1;
	protected static int petgasPerDepositMax = 1;

	public float rot = 0;
	public float prevRot = 0;
	public float speed = 0;

	@Override
	public String getName() {
		return "container.pumpjack";
	}

	@Override
	public long getEnergyCapacityQuanta() {
		return maxPower;
	}

	@Override
	public int getPowerReq() {
		return consumption;
	}

	@Override
	public int getDelay() {
		return delay;
	}

    @Override public ExtractionProfile getExtractionProfile() {
        return new ExtractionProfile(50, oilPerDeposit, oilPerDunaDeposit, oilPerBedrockDepsoit,
                gasPerDepositMin, gasPerDepositMax, gasPerBedrockDepositMin, gasPerBedrockDepositMax,
                gasPerDeposit, petgasPerDepositMin, petgasPerDepositMax, drainChance, drainChanceDuna, 0);
    }

	@Override
	public void onDrill(int y) {
		Block b = worldObj.getBlock(xCoord, y, zCoord);
		ItemStack stack = new ItemStack(b);
		int[] ids = OreDictionary.getOreIDs(stack);
		for(Integer i : ids) {
			String name = OreDictionary.getOreName(i);

			if("oreUranium".equals(name)) {
				for(int j = 2; j < 6; j++) {
					ForgeDirection dir = ForgeDirection.getOrientation(j);
					if(worldObj.getBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ).isReplaceable(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ)) {
						worldObj.setBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, ModBlocks.gas_radon_dense);
					}
				}
			}

			if("oreAsbestos".equals(name)) {
				for(int j = 2; j < 6; j++) {
					ForgeDirection dir = ForgeDirection.getOrientation(j);
					if(worldObj.getBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ).isReplaceable(worldObj, xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ)) {
						worldObj.setBlock(xCoord + dir.offsetX, yCoord, zCoord + dir.offsetZ, ModBlocks.gas_asbestos);
					}
				}
			}
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if(worldObj.isRemote) {

			this.prevRot = rot;

			if(this.indicator == 0) {
				this.rot += speed;
			}

			if(this.rot >= 360) {
				this.prevRot -= 360;
				this.rot -= 360;
			}
		}
	}

	@Override
	public void networkPack(NBTTagCompound nbt, int range) {
		nbt.setFloat("speed", this.indicator == 0 ? (5F + (2F * this.speedLevel)) + (this.overLevel - 1F) * 10: 0F);

		super.networkPack(nbt, range);
	}

	@Override
	public void networkUnpack(NBTTagCompound nbt) {
		super.networkUnpack(nbt);

		this.speed = nbt.getFloat("speed");
	}

	@Override
	public void onSuck(int x, int y, int z) {
        if(extractDeposit(x, y, z, 50, oilPerDeposit, oilPerDunaDeposit, oilPerBedrockDepsoit,
                gasPerDepositMin, gasPerDepositMax, gasPerBedrockDepositMin, gasPerBedrockDepositMax,
                gasPerDeposit, petgasPerDepositMin, petgasPerDepositMax, drainChance, drainChanceDuna)) {
            worldObj.playSoundEffect(xCoord, yCoord, zCoord, "game.neutral.swim.splash", 2F, .5F);
        }
    }


	AxisAlignedBB bb = null;

	@Override
	public AxisAlignedBB getRenderBoundingBox() {

		if(bb == null) {
			bb = AxisAlignedBB.getBoundingBox(
					xCoord - 7,
					yCoord,
					zCoord - 7,
					xCoord + 8,
					yCoord + 6,
					zCoord + 8
					);
		}

		return bb;
	}

	@Override
	public DirPos[] getConPos() {
		this.getBlockMetadata();
		ForgeDirection dir = ForgeDirection.getOrientation(this.blockMetadata - BlockDummyable.offset);
		ForgeDirection rot = dir.getRotation(ForgeDirection.DOWN);

		return new DirPos[] {
			new DirPos(xCoord + rot.offsetX * 2 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2 + dir.offsetZ * 2, dir),
			new DirPos(xCoord + rot.offsetX * 2 + dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 4 - dir.offsetZ * 2, dir.getOpposite()),
			new DirPos(xCoord + rot.offsetX * 4 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 4 + dir.offsetZ * 2, dir),
			new DirPos(xCoord + rot.offsetX * 4 - dir.offsetX * 2, yCoord, zCoord + rot.offsetZ * 2 - dir.offsetZ * 2, dir.getOpposite())
		};
	}

	@Override
	public String getConfigName() {
		return "pumpjack";
	}

	@Override
	public void readIfPresent(JsonObject obj) {
		maxPower = IConfigurableMachine.grabEnergyQuanta(obj, "I:energyCapacityQuanta", "I:powerCap", maxPower);
		consumption = IConfigurableMachine.grab(obj, "I:consumption", consumption);
		delay = IConfigurableMachine.grab(obj, "I:delay", delay);
		oilPerDeposit = IConfigurableMachine.grab(obj, "I:oilPerDeposit", oilPerDeposit);
		gasPerDepositMin = IConfigurableMachine.grab(obj, "I:gasPerDepositMin", gasPerDepositMin);
		gasPerDepositMax = IConfigurableMachine.grab(obj, "I:gasPerDepositMax", gasPerDepositMax);
		drainChance = IConfigurableMachine.grab(obj, "D:drainChance", drainChance);
	}

	@Override
	public void writeConfig(JsonWriter writer) throws IOException {
		writer.name("I:energyCapacityQuanta").value(maxPower);
		writer.name("I:consumption").value(consumption);
		writer.name("I:delay").value(delay);
		writer.name("I:oilPerDeposit").value(oilPerDeposit);
		writer.name("I:gasPerDepositMin").value(gasPerDepositMin);
		writer.name("I:gasPerDepositMax").value(gasPerDepositMax);
		writer.name("D:drainChance").value(drainChance);
	}

	@Override
	public Container provideContainer(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new ContainerMachineOilWell(player.inventory, this);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Object provideGUI(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return new GUIMachineOilWell(player.inventory, this);
	}

	@Override
	public void provideInfo(UpgradeType type, int level, List<String> info, boolean extendedInfo) {
		info.add(IUpgradeInfoProvider.getStandardLabel(ModBlocks.machine_pumpjack));
		if(type == UpgradeType.SPEED) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_DELAY, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_CONSUMPTION, "+" + (level * 25) + "%"));
		}
		if(type == UpgradeType.POWER) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_CONSUMPTION, "-" + (level * 25) + "%"));
			info.add(EnumChatFormatting.RED + I18nUtil.resolveKey(KEY_DELAY, "+" + (level * 10) + "%"));
		}
		if(type == UpgradeType.AFTERBURN) {
			info.add(EnumChatFormatting.GREEN + I18nUtil.resolveKey(KEY_BURN, level * 10, level * 50));
		}
		if(type == UpgradeType.OVERDRIVE) {
			info.add((BobMathUtil.getBlink() ? EnumChatFormatting.RED : EnumChatFormatting.DARK_GRAY) + "YES");
		}
	}
}
