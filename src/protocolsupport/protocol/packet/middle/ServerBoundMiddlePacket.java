package protocolsupport.protocol.packet.middle;

import java.io.IOException;

import protocolsupport.protocol.packet.middleimpl.PacketCreator;
import protocolsupport.protocol.serializer.ProtocolSupportPacketDataSerializer;
import protocolsupport.utils.recyclable.RecyclableCollection;

public abstract class ServerBoundMiddlePacket extends MiddlePacket {

	public abstract void readFromClientData(ProtocolSupportPacketDataSerializer serializer) throws IOException;

	public abstract RecyclableCollection<PacketCreator> toNative() throws Exception;

}
