package com.hbm.realsim;

import static org.junit.Assert.*;
import org.junit.Test;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import com.hbm.packet.toclient.NuclearSeismicPacket;
import com.hbm.tileentity.machine.TileEntityMachineArcFurnace;

public class ArcFurnacePacketTest {
    @Test public void emptyRequiredChoicesDoNotBecomeNeiFirePlaceholders() {
        assertNull(com.hbm.handler.nei.NEISafe.positionedStack(java.util.Collections.emptyList(), 0, 0));
        assertNull(com.hbm.handler.nei.NEISafe.positionedStack(new net.minecraft.item.ItemStack[0], 0, 0));
    }
    @Test public void smallFurnaceStateRoundTripsWithoutLegacyGaugeFields() {
        TileEntityMachineArcFurnace server = new TileEntityMachineArcFurnace();
        server.energyQuanta = 40001; server.dualCookTime = 17;
        ByteBuf bytes = Unpooled.buffer();
        try {
            server.serialize(bytes); assertEquals(13, bytes.readableBytes());
            TileEntityMachineArcFurnace client = new TileEntityMachineArcFurnace();
            client.deserialize(bytes);
            assertEquals(server.energyQuanta, client.energyQuanta);
            assertEquals(server.dualCookTime, client.dualCookTime);
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
    }
    @Test public void reportedTwentyBytePayloadIsRejectedBeforeSeismicReads() {
        ByteBuf bytes = Unpooled.buffer().writeZero(20);
        try {
            NuclearSeismicPacket packet = new NuclearSeismicPacket(); packet.fromBytes(bytes);
            assertEquals(0, bytes.readableBytes());
            // A malformed event must never reach Minecraft or produce a camera cue.
            assertNull(new NuclearSeismicPacket.Handler().onMessage(packet, null));
        } finally { bytes.release(); }
    }
    @Test public void seismicWireFormatStillRoundTripsAtFiftySixBytes() {
        ByteBuf bytes = Unpooled.buffer(), roundTrip = Unpooled.buffer();
        try {
            new NuclearSeismicPacket(1, 2, 3, 4, 0.5, 6, 100, 0.25F).toBytes(bytes);
            assertEquals(56, bytes.readableBytes());
            NuclearSeismicPacket decoded = new NuclearSeismicPacket(); decoded.fromBytes(bytes); decoded.toBytes(roundTrip);
            assertEquals(bytes.writerIndex(), roundTrip.writerIndex());
            for(int i = 0; i < bytes.writerIndex(); i++) assertEquals(bytes.getByte(i), roundTrip.getByte(i));
        } finally { bytes.release(); roundTrip.release(); }
    }
}
