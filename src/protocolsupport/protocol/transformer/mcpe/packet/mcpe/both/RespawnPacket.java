package protocolsupport.protocol.transformer.mcpe.packet.mcpe.both;

import io.netty.buffer.ByteBuf;

import java.util.Collections;
import java.util.List;

import net.minecraft.server.v1_8_R3.Packet;
import protocolsupport.protocol.transformer.mcpe.packet.mcpe.ClientboundPEPacket;
import protocolsupport.protocol.transformer.mcpe.packet.mcpe.DualPEPacket;
import protocolsupport.protocol.transformer.mcpe.packet.mcpe.PEPacketIDs;
import protocolsupport.protocol.transformer.mcpe.packet.mcpe.ServerboundPEPacket;

public class RespawnPacket implements DualPEPacket {

	protected int entityId;
	protected float x;
	protected float y;
	protected float z;

	public RespawnPacket() {
	}

	public RespawnPacket(int entityId, float x, float y, float z) {
		this.entityId = entityId;
		this.x = x;
		this.y = y;
		this.z = z;
		
	} 

	@Override
	public int getId() {
		return PEPacketIDs.RESPAWN_PACKET;
	}

	@Override
	public ServerboundPEPacket decode(ByteBuf buf) throws Exception {
		entityId = buf.readInt();
		x = buf.readFloat();
		y = buf.readFloat();
		z = buf.readFloat();
		return this;
	}

	@Override
	public ClientboundPEPacket encode(ByteBuf buf) throws Exception {
		buf.writeInt(entityId);
		buf.writeFloat(x);
		buf.writeFloat(y);
		buf.writeFloat(z);
		return this;
	}

	@Override
	public List<? extends Packet<?>> transfrom() throws Exception {
	  //TODO: ???
		return Collections.emptyList();
	}

}
